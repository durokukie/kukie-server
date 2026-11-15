package com.duro.kukie.modelcatalog.application

import com.duro.kukie.cluster.application.ClusterPermission
import com.duro.kukie.cluster.application.ServedModelName
import com.duro.kukie.global.util.logger
import com.duro.kukie.modelcatalog.domain.ModelFavoriteRepository
import com.duro.kukie.modelcatalog.presentation.dto.response.ModelFavoritesResponse
import com.github.f4b6a3.uuid.UuidCreator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 클러스터별 후보 즐겨찾기. 보기는 클러스터를 볼 수 있는 사람, 넣고 빼기는 팀 Admin 이다.
 * 후보에서 모델을 시작하면 rememberStarted 로 저절로 들어간다 — 측정한 모델이 다운로드 순위에서 밀려도 후보에 남게.
 */
@Service
class ModelFavoriteService(
    private val clusterPermission: ClusterPermission,
    private val modelFavoriteRepository: ModelFavoriteRepository,
) {

    private val log = logger()

    @Transactional(readOnly = true)
    fun list(clusterId: UUID, userId: UUID): ModelFavoritesResponse {
        clusterPermission.requireReadable(clusterId, userId)
        return favoritesOf(clusterId)
    }

    @Transactional
    fun add(clusterId: UUID, userId: UUID, model: String): ModelFavoritesResponse {
        clusterPermission.requireWritable(clusterId, userId)
        modelFavoriteRepository.insertIfAbsent(UuidCreator.getTimeOrderedEpoch(), clusterId, model, userId)
        return favoritesOf(clusterId)
    }

    /** 없는 모델을 빼도 실패하지 않는다 — 화면이 두 번 눌러도 같은 결과다. */
    @Transactional
    fun remove(clusterId: UUID, userId: UUID, model: String): ModelFavoritesResponse {
        clusterPermission.requireWritable(clusterId, userId)
        modelFavoriteRepository.deleteByClusterIdAndModelId(clusterId, model)
        return favoritesOf(clusterId)
    }

    /**
     * 모델을 시작한 뒤에 부른다. 권한은 시작이 이미 확인했다. 즐겨찾기를 못 남겨도 시작은 성공이라 예외를 삼키고 남긴다.
     * 트랜잭션을 여기 걸지 않는다 — 걸면 삼킨 예외가 rollback-only 로 남아 커밋 때 다시 터진다. 넣기는 저장소 메서드의 트랜잭션 하나다.
     */
    fun rememberStarted(clusterId: UUID, userId: UUID, model: String) {
        if (!ServedModelName.validModel(model)) return
        try {
            modelFavoriteRepository.insertIfAbsent(UuidCreator.getTimeOrderedEpoch(), clusterId, model, userId)
        } catch (exception: Exception) {
            log.warn("시작한 모델을 즐겨찾기에 남기지 못했습니다: cluster={}, model={}", clusterId, model, exception)
        }
    }

    private fun favoritesOf(clusterId: UUID) =
        ModelFavoritesResponse(modelFavoriteRepository.findAllByClusterIdOrderByCreatedAt(clusterId).map { it.modelId })
}
