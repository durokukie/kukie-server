package com.duro.kukie.modelcatalog.application

import com.duro.kukie.global.config.properties.HuggingFaceProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.modelcatalog.application.port.out.HuggingFaceClient
import com.duro.kukie.modelcatalog.domain.CollectedModel
import com.duro.kukie.modelcatalog.domain.HuggingFaceModel
import com.duro.kukie.modelcatalog.domain.ModelCandidate
import com.duro.kukie.modelcatalog.domain.ModelCandidateRefreshLock
import com.duro.kukie.modelcatalog.domain.ModelCandidateRepository
import com.duro.kukie.modelcatalog.domain.ModelCandidateRules
import com.duro.kukie.modelcatalog.domain.ModelFavoriteRepository
import com.duro.kukie.modelcatalog.domain.VerifiedModels
import com.duro.kukie.modelcatalog.exception.ModelCandidateRefreshFailedException
import com.duro.kukie.modelcatalog.exception.ModelCandidateRefreshTooSoonException
import com.duro.kukie.modelcatalog.presentation.dto.response.ModelCandidatesResponse
import com.duro.kukie.team.application.TeamPermission
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

@Service
class RefreshModelCandidatesService(
    private val huggingFaceClient: HuggingFaceClient,
    private val modelCandidateRepository: ModelCandidateRepository,
    private val modelFavoriteRepository: ModelFavoriteRepository,
    private val modelCandidateRefreshLock: ModelCandidateRefreshLock,
    private val teamPermission: TeamPermission,
    private val huggingFaceProperties: HuggingFaceProperties,
    private val transactionTemplate: TransactionTemplate,
) {

    private val log = logger()

    operator fun invoke(userId: UUID): ModelCandidatesResponse {
        teamPermission.requireAnyTeamAdmin(userId)
        val lockToken = modelCandidateRefreshLock.tryAcquire(huggingFaceProperties.refreshLockTimeout)
            ?: throw ModelCandidateRefreshTooSoonException()

        val response = try {
            val collected = collect()
            val collectedAt = utcNow()
            requireNotNull(transactionTemplate.execute { replaceAll(collected, collectedAt) })
        } catch (exception: Exception) {
            releaseQuietly(lockToken)
            throw exception
        }
        startCooldown(lockToken)

        return response
    }

    /** 이미 저장한 뒤라 실패로 돌리지 않는다. 그러면 잠금이 refreshLockTimeout 까지 남는다. */
    private fun startCooldown(lockToken: String) {
        try {
            modelCandidateRefreshLock.holdFor(lockToken, huggingFaceProperties.refreshCooldown)
        } catch (exception: Exception) {
            log.warn("모델 후보 갱신 대기 시간을 정하지 못했습니다", exception)
        }
    }

    /** 잠금을 못 풀어도 시간이 지나면 풀린다. 원래 실패가 가려지지 않게 삼킨다. */
    private fun releaseQuietly(lockToken: String) {
        try {
            modelCandidateRefreshLock.release(lockToken)
        } catch (exception: Exception) {
            log.warn("모델 후보 갱신 잠금을 풀지 못했습니다", exception)
        }
    }

    private fun collect(): List<CollectedModel> {
        // 전체 다운로드 순으로 한 번에 받으면 몇 회사가 자리를 다 차지해 나머지 회사가 목록에 들지 못한다
        val listing = ModelCandidateRules.AUTHOR_ACCOUNTS.flatMap { author ->
            huggingFaceClient.fetchPopular(author, PER_AUTHOR_LIMIT).also {
                if (it.isEmpty()) log.warn("허깅페이스가 {} 계정의 모델을 하나도 주지 않았습니다. 계정 이름이 바뀌었는지 확인하세요", author)
            }
        }
        if (listing.isEmpty()) throw refreshFailed("허깅페이스가 빈 목록을 돌려줬습니다")

        val favoriteIds = modelFavoriteRepository.findAllModelIds()
        val verified = verifiedModelsOffTheList(listing)
        val selection = ModelCandidateRules.select(listing + verified + favoritesOffTheList(favoriteIds, listing + verified))
        VerifiedModels.MODELS.keys.firstOrNull { it in selection.dropped }?.let {
            throw refreshFailed("검증된 모델이 규칙에 걸렸습니다: $it: ${selection.dropped[it]}")
        }
        val spread = ModelCandidateRules.spreadAuthors(selection.kept, PER_AUTHOR, VerifiedModels.MODELS.keys + favoriteIds)
        log.info("모델 후보를 모았습니다: 후보={}, 걸러짐={}, 회사별 수 넘김={}", spread.size, selection.dropped.size, selection.kept.size - spread.size)

        val collected = spread.map { ModelCandidateRules.withRepoFiles(it, huggingFaceClient.fetchModel(it.id)) }
        collected.firstOrNull { VerifiedModels.MODELS[it.id]?.let { verified -> !ModelCandidateRules.fitsMeasured(it, verified) } == true }?.let {
            throw refreshFailed("검증된 모델이 실측한 GPU 에 들어가지 않는다고 나옵니다: ${it.id}, 가중치 ${it.weightGb}GB")
        }
        return collected
    }

    private fun verifiedModelsOffTheList(listing: List<HuggingFaceModel>): List<HuggingFaceModel> {
        val listed = listing.map { it.id }.toSet()
        return VerifiedModels.MODELS.keys.filter { it !in listed }.map { modelId ->
            huggingFaceClient.fetchModel(modelId).also {
                if (it.id != modelId) throw refreshFailed("검증된 모델의 이름이 다릅니다: $modelId -> ${it.id}")
            }
        }
    }

    /**
     * 어느 클러스터든 즐겨찾기한 모델 중 목록에 없는 것을 따로 받는다. 검증 모델과 달리 하나를 못 읽어도(허깅페이스에서
     * 지워짐·이름 바뀜) 갱신을 실패시키지 않고 그 모델만 뺀다. 규칙에 걸리면 다른 후보처럼 빠진다.
     */
    private fun favoritesOffTheList(favoriteIds: List<String>, listing: List<HuggingFaceModel>): List<HuggingFaceModel> {
        val listed = listing.map { it.id }.toSet()
        val offTheList = favoriteIds.filter { it !in listed }
        if (offTheList.size > MAX_FAVORITES_OFF_THE_LIST) {
            log.warn("순위 밖 즐겨찾기가 {}개라 앞 {}개만 받습니다", offTheList.size, MAX_FAVORITES_OFF_THE_LIST)
        }
        // 갱신은 잠금을 쥔 채 하나씩 부른다 — 수를 묶어 허깅페이스가 느려도 갱신이 끝없이 길어지지 않게
        return offTheList.take(MAX_FAVORITES_OFF_THE_LIST).mapNotNull { modelId ->
            try {
                huggingFaceClient.fetchModel(modelId).takeIf { it.id == modelId }
                    ?: null.also { log.warn("즐겨찾기 모델의 이름이 달라 후보에서 뺍니다: {}", modelId) }
            } catch (exception: ModelCandidateRefreshFailedException) {
                log.warn("즐겨찾기 모델을 허깅페이스에서 읽지 못해 후보에서 뺍니다: {}", modelId)
                null
            }
        }
    }

    private fun replaceAll(collected: List<CollectedModel>, collectedAt: LocalDateTime): ModelCandidatesResponse {
        val existing = modelCandidateRepository.findAll().associateBy { it.modelId }
        val keptIds = collected.map { it.id }.toSet()
        modelCandidateRepository.deleteAll(existing.values.filter { it.modelId !in keptIds })

        val saved = collected.mapIndexed { order, model ->
            existing[model.id]?.apply { refresh(model, order, collectedAt) }
                ?: modelCandidateRepository.save(ModelCandidate(model.id, model, order, collectedAt))
        }
        return ModelCandidatesResponse.from(saved)
    }

    private fun refreshFailed(reason: String): ModelCandidateRefreshFailedException {
        log.warn("모델 후보를 갱신하지 못해 저장된 목록을 그대로 둡니다: {}", reason)
        return ModelCandidateRefreshFailedException()
    }

    companion object {
        /** 회사 목록 상위도 소형 모델이 많다. 2026-11-13(KST) 실제 목록에서 50개면 29곳 중 25곳이 크기 규칙을 지나 1개 이상 남았다 */
        private const val PER_AUTHOR_LIMIT = 50

        /** 남은 후보마다 단건 API 를 더 부르므로 29곳 × 3 = 87번 안쪽으로 묶는다(한 번 약 0.3초) */
        private const val PER_AUTHOR = 3

        /** 순위 밖 즐겨찾기를 따로 받는 수 상한. 넘으면 최근에 넣은 것부터 남기고 경고를 남긴다 */
        private const val MAX_FAVORITES_OFF_THE_LIST = 30
    }
}
