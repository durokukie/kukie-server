package com.duro.kukie.team.presentation.dto.response

import com.duro.kukie.team.domain.InvitationStatus
import com.duro.kukie.team.domain.TeamInvitation
import com.duro.kukie.user.domain.User
import java.time.LocalDateTime
import java.util.UUID

/** 팀이 보낸 초대 한 건. 처리된 초대도 기록으로 함께 나간다. */
data class SentTeamInvitationResponse(
    val id: UUID,
    val email: String,
    val status: InvitationStatus,
    val invitedBy: InviterResponse,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val respondedAt: LocalDateTime?,
) {
    companion object {
        fun of(invitation: TeamInvitation, inviter: User?): SentTeamInvitationResponse {
            val status = invitation.currentStatus

            return SentTeamInvitationResponse(
                id = invitation.id,
                email = invitation.email.value,
                status = status,
                invitedBy = InviterResponse(invitation.invitedBy, inviter?.name),
                createdAt = invitation.createdAt,
                expiresAt = invitation.expiresAt,
                respondedAt = if (status in RESPONDED) invitation.updatedAt else null,
            )
        }

        /** 이 상태들은 더 바뀌지 않으므로 updatedAt 이 처리된 시각이다. */
        private val RESPONDED = setOf(InvitationStatus.ACCEPTED, InvitationStatus.DECLINED, InvitationStatus.CANCELED)
    }
}

data class InviterResponse(
    val id: UUID,
    val name: String?,
)
