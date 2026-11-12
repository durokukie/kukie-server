package com.duro.kukie.serving.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "tbl_serving_usage")
class ServingUsage(keyId: UUID, accessId: UUID, clusterId: UUID, userId: UUID, model: String?, startedAt: LocalDateTime, deadlineAt: LocalDateTime) : BaseTimeEntity() {
    @Id var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set
    @Column(name = "key_id", nullable = false) var keyId = keyId
        protected set
    @Column(name = "access_id", nullable = false) var accessId = accessId
        protected set
    @Column(name = "cluster_id", nullable = false) var clusterId = clusterId
        protected set
    @Column(name = "user_id", nullable = false) var userId = userId
        protected set
    @Column(length = 200) var model = model
        protected set
    @Column(name = "started_at", nullable = false) var startedAt = startedAt
        protected set
    @Column(name = "deadline_at", nullable = false) var deadlineAt = deadlineAt
        protected set
    @Column(name = "finished_at") var finishedAt: LocalDateTime? = null
        protected set
    @Column(name = "duration_millis") var durationMillis: Long? = null
        protected set
    @Column(name = "http_status") var httpStatus: Int? = null
        protected set
    @Column(name = "wire_status") var wireStatus: Int? = null
        protected set
    @Column(nullable = false, length = 16) var outcome = "IN_PROGRESS"
        protected set
    @Column(name = "input_tokens") var inputTokens: Long? = null
        protected set
    @Column(name = "output_tokens") var outputTokens: Long? = null
        protected set
    @Column(name = "usage_missing", nullable = false) var usageMissing = model != null
        protected set
    @Column(name = "ttfb_millis") var ttfbMillis: Long? = null
        protected set
}
