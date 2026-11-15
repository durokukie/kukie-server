package com.duro.kukie.serving.domain

import com.duro.kukie.global.domain.Email
import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "tbl_serving_access")
class ServingAccess(
    clusterId: UUID,
    email: Email,
    invitedBy: UUID,
    invitationExpiresAt: LocalDateTime,
) : BaseTimeEntity() {
    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "cluster_id", nullable = false)
    var clusterId = clusterId
        protected set

    @Column(nullable = false, length = 255)
    var email = email
        protected set

    @Column(name = "invited_by", nullable = false)
    var invitedBy = invitedBy
        protected set

    @Column(name = "user_id")
    var userId: UUID? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status = ServingAccessStatus.PENDING
        protected set

    @Column(name = "invitation_expires_at", nullable = false)
    var invitationExpiresAt = invitationExpiresAt
        protected set

    fun statusAt(now: LocalDateTime): ServingAccessStatus =
        if (status == ServingAccessStatus.PENDING && !invitationExpiresAt.isAfter(now)) ServingAccessStatus.EXPIRED else status

    fun accept(userId: UUID, email: Email, now: LocalDateTime) {
        requireOwner(email)
        requirePending(now)
        this.userId = userId
        status = ServingAccessStatus.ACTIVE
    }

    fun decline(email: Email, now: LocalDateTime) {
        requireOwner(email)
        requirePending(now)
        status = ServingAccessStatus.DECLINED
    }

    fun revoke() {
        if (status != ServingAccessStatus.PENDING && status != ServingAccessStatus.ACTIVE) {
            throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_NOT_ACTIVE)
        }
        status = ServingAccessStatus.REVOKED
    }

    fun expire(now: LocalDateTime) {
        check(statusAt(now) == ServingAccessStatus.EXPIRED)
        status = ServingAccessStatus.EXPIRED
    }

    private fun requireOwner(email: Email) {
        if (this.email != email) throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_NOT_FOUND)
    }

    private fun requirePending(now: LocalDateTime) {
        if (status != ServingAccessStatus.PENDING) throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_NOT_ACTIVE)
        if (!invitationExpiresAt.isAfter(now)) throw ServingAccessException(ServingErrorCode.SERVING_INVITATION_EXPIRED)
    }
}

enum class ServingAccessStatus { PENDING, ACTIVE, DECLINED, REVOKED, EXPIRED }
