package com.duro.kukie.team.presentation.dto.request

import com.duro.kukie.team.application.port.`in`.AcceptTeamInvitationLinkCommand
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class AcceptTeamInvitationLinkRequest(
    @field:NotBlank
    val token: String,
) {
    fun toCommand(userId: UUID) = AcceptTeamInvitationLinkCommand(
        userId = userId,
        token = token,
    )
}
