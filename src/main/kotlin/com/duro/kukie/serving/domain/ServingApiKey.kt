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
@Table(name = "tbl_serving_api_key")
class ServingApiKey(
    accessId: UUID,
    name: String,
    tokenHash: String,
    prefix: String,
    expiresAt: LocalDateTime,
) : BaseTimeEntity() {
    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set
    @Column(name = "access_id", nullable = false)
    var accessId = accessId
        protected set
    @Column(nullable = false, length = 80)
    var name = name
        protected set
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    var tokenHash = tokenHash
        protected set
    @Column(nullable = false, length = 12)
    var prefix = prefix
        protected set
    @Column(name = "expires_at", nullable = false)
    var expiresAt = expiresAt
        protected set
    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null
        protected set

    fun isUsable(now: LocalDateTime): Boolean = revokedAt == null && expiresAt.isAfter(now)

    fun revoke(now: LocalDateTime) {
        if (revokedAt == null) revokedAt = now
    }
}
