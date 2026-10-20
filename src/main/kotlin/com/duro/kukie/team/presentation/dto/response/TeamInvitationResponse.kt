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
            email = invitation.email.value,
            status = invitation.currentStatus,
            expiresAt = invitation.expiresAt,
        )
    }
}
