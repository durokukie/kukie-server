package com.duro.kukie.team.presentation.dto.response

import com.duro.kukie.team.domain.InvitationStatus
import com.duro.kukie.team.domain.TeamInvitation
import java.util.UUID

/** 보낸 초대 하나. 초대는 언제나 대기 상태로 시작한다 — 받는 사람이 수락해야 멤버가 된다. */
data class TeamInvitationResponse(
    val id: UUID,
    val email: String,
    val status: InvitationStatus,
) {
    companion object {
        fun of(invitation: TeamInvitation) = TeamInvitationResponse(
            id = invitation.id,
            email = invitation.email,
            status = invitation.status,
        )
    }
}
