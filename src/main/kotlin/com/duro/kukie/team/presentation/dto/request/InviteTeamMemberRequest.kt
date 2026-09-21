package com.duro.kukie.team.presentation.dto.request

import com.duro.kukie.team.application.port.`in`.InviteTeamMemberCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class InviteTeamMemberRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
) {
    fun toCommand(teamId: UUID, userId: UUID) = InviteTeamMemberCommand(
        teamId = teamId,
        userId = userId,
        email = email,
    )
}
