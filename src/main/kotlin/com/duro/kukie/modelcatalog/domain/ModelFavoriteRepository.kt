package com.duro.kukie.modelcatalog.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ModelFavoriteRepository : JpaRepository<ModelFavorite, UUID> {

    fun findAllByClusterIdOrderByCreatedAt(clusterId: UUID): List<ModelFavorite>

    /** 어느 클러스터든 즐겨찾기한 모델, 최근에 넣은 것부터 — 후보 목록 갱신이 순위 밖이어도 따로 받고, 상한이면 앞에서부터 남긴다. */
    @Query("select f.modelId from ModelFavorite f group by f.modelId order by max(f.createdAt) desc, f.modelId")
    fun findAllModelIds(): List<String>

    /**
     * 이미 있으면 아무 일도 하지 않는다. 같은 클러스터·모델을 동시에 넣어도 유일 제약에 걸려 실패하지 않게 DB 에서 한 번에 넣는다.
     * created_at·updated_at 은 엔티티를 거치지 않아 여기서 채운다 — 다른 표처럼 UTC 로.
     */
    @Transactional
    @Modifying
    @Query(
        value = "insert into tbl_model_favorite (id, cluster_id, model_id, created_by, created_at, updated_at) " +
            "values (:id, :clusterId, :modelId, :createdBy, now() at time zone 'utc', now() at time zone 'utc') " +
            "on conflict (cluster_id, model_id) do nothing",
        nativeQuery = true,
    )
    fun insertIfAbsent(id: UUID, clusterId: UUID, modelId: String, createdBy: UUID): Int

    @Modifying
    fun deleteByClusterIdAndModelId(clusterId: UUID, modelId: String): Long
}
