package com.duro.kukie.team.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.global.util.normalizeEmail
import com.duro.kukie.team.exception.InvitationNotPendingException
import com.duro.kukie.team.exception.NotMyInvitationException
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * 팀이 어떤 이메일 주소를 초대한 기록.
 *
 * 회원 id가 아니라 **이메일**을 들고 있다. 아직 가입하지 않은 사람도 초대할 수 있어야 하고,
 * 그 사람은 가입한 뒤에 자기 Inbox 에서 이 초대를 보게 된다 (제품기획서 02 §4).
 */
@Entity
@Table(name = "tbl_team_invitation")
class TeamInvitation(
    teamId: UUID,
    email: String,
    invitedBy: UUID,
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

    /**
     * 초대는 회원 id 가 아니라 주소로 사람을 가리키므로 "내 주소로 온 초대인가" 가 본인 확인이다.
     * 수락과 거절이 같은 규칙을 쓰므로 두 서비스에 복붙하지 않고 여기 둔다 (자동 리뷰 지적).
     */
    fun requireOwnedBy(email: String) {
        if (this.email != email.normalizeEmail()) {
            throw NotMyInvitationException()
        }
    }

    /** 이 초대가 이 팀의 것인지. 다른 팀 관리자가 남의 초대를 취소하지 못하게 막는다. */
    fun belongsTo(teamId: UUID): Boolean = this.teamId == teamId

    fun accept() {
        requirePending()
        status = InvitationStatus.ACCEPTED
    }

    fun decline() {
        requirePending()
        status = InvitationStatus.DECLINED
    }

    /** 보낸 쪽(관리자)이 거둬들인다. */
    fun cancel() {
        requirePending()
        status = InvitationStatus.CANCELED
    }

    /** 이미 수락·거절·취소한 초대를 다시 처리하지 못하게 막는다. */
    private fun requirePending() {
        if (status.isPending.not()) {
            throw InvitationNotPendingException()
        }
    }
}
