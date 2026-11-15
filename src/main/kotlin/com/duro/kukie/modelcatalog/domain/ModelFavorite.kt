package com.duro.kukie.modelcatalog.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** 클러스터마다 고정해 둔 후보 모델. 후보 목록 갱신이 다운로드 순위와 상관없이 남긴다. */
@Entity
@Table(name = "tbl_model_favorite")
class ModelFavorite(
    clusterId: UUID,
    modelId: String,
    createdBy: UUID,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "cluster_id", nullable = false)
    var clusterId = clusterId
        protected set

    @Column(name = "model_id", nullable = false, length = ModelCandidate.MAX_MODEL_ID_LENGTH)
    var modelId = modelId
        protected set

    @Column(name = "created_by", nullable = false)
    var createdBy = createdBy
        protected set
}
