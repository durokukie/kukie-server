package com.duro.kukie.team.domain

import com.duro.kukie.global.domain.Email
import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.team.exception.InvitationExpiredException
import com.duro.kukie.team.exception.InvitationNotPendingException
import com.duro.kukie.team.exception.NotMyInvitationException
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/** 팀이 이메일 주소를 초대한 기록. 가입 전인 사람도 초대할 수 있게 회원 id 대신 주소를 든다. */
@Entity
@Table(name = "tbl_team_invitation")
class TeamInvitation(
    teamId: UUID,
    email: Email,
    invitedBy: UUID,
    expiresAt: LocalDateTime = LocalDateTime.now().plus(VALIDITY),
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "team_id", nullable = false)
    var teamId = teamId
        protected set

    @Column(nullable = false, length = 255)
    var email = email
        protected set

    @Column(name = "invited_by", nullable = false)
    var invitedBy = invitedBy
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status = InvitationStatus.PENDING
        protected set

    @Column(name = "expires_at", nullable = false)
    var expiresAt = expiresAt
        protected set

    val isExpired: Boolean
        get() = expiresAt.isBefore(LocalDateTime.now())

    /** 초대는 주소로 사람을 가리키므로 주소 일치가 본인 확인이다. */
    fun requireOwnedBy(email: Email) {
        if (this.email != email) {
            throw NotMyInvitationException()
        }
    }

    fun accept() {
        requirePending()
        requireNotExpired()
        status = InvitationStatus.ACCEPTED
    }

    fun decline() {
        requirePending()
        requireNotExpired()
        status = InvitationStatus.DECLINED
    }

    /** 보낸 쪽(관리자)이 거둬들인다. 만료된 초대도 취소할 수 있다 — 어차피 쓸 수 없는 초대다. */
    fun cancel() {
        requirePending()
        status = InvitationStatus.CANCELED
    }

    /** 기한이 지난 PENDING 초대를 정리한다. 같은 주소를 다시 초대할 때 불린다. */
    fun expire() {
        requirePending()
        status = InvitationStatus.EXPIRED
    }

    /** 이미 수락·거절·취소한 초대를 다시 처리하지 못하게 막는다. */
    private fun requirePending() {
        if (status != InvitationStatus.PENDING) {
            throw InvitationNotPendingException()
        }
    }

    private fun requireNotExpired() {
        if (isExpired) {
            throw InvitationExpiredException()
        }
    }

    companion object {
        /** 초대의 유효 기간. 만료 시각 계산과 메일 본문의 안내가 같은 값을 본다. */
        val VALIDITY: Duration = Duration.ofDays(7)
    }
}
