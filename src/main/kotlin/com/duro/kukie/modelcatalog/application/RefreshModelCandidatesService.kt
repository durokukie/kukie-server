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
        val listing = huggingFaceClient.fetchTrending(TRENDING_LIMIT)
        if (listing.isEmpty()) throw refreshFailed("허깅페이스가 빈 목록을 돌려줬습니다")

        val selection = ModelCandidateRules.select(listing + verifiedModelsOffTheList(listing))
        VerifiedModels.MODELS.keys.firstOrNull { it in selection.dropped }?.let {
            throw refreshFailed("검증된 모델이 규칙에 걸렸습니다: $it: ${selection.dropped[it]}")
        }
        log.info("모델 후보를 모았습니다: 후보={}, 걸러짐={}", selection.kept.size, selection.dropped.size)

        val collected = selection.kept.map { ModelCandidateRules.withRepoFiles(it, huggingFaceClient.fetchModel(it.id)) }
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
        private const val TRENDING_LIMIT = 50
    }
}
