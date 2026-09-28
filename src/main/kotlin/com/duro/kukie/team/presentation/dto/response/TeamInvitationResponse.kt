package com.duro.kukie.team.presentation.dto.response

import com.duro.kukie.team.domain.InvitationStatus
import com.duro.kukie.team.domain.TeamInvitation
import java.time.LocalDateTime
import java.util.UUID

data class TeamInvitationResponse(
    val id: UUID,
    val email: String,
    val status: InvitationStatus,
    val expiresAt: LocalDateTime,
) {
    companion object {
        fun of(invitation: TeamInvitation) = TeamInvitationResponse(
            id = invitation.id,
            email = invitation.email,
            status = statusOf(invitation),
            expiresAt = invitation.expiresAt,
        )

        /** 만료는 저장하지 않고 expiresAt 으로 계산한다 — 기한이 지난 PENDING 은 EXPIRED 로 나간다. */
        private fun statusOf(invitation: TeamInvitation): InvitationStatus =
            if (invitation.status == InvitationStatus.PENDING && invitation.isExpired) InvitationStatus.EXPIRED else invitation.status
    }
}
