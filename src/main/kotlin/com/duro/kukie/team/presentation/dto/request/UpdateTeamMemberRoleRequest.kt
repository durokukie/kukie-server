package com.duro.kukie.team.presentation.dto.request

import com.duro.kukie.team.application.port.`in`.UpdateTeamMemberRoleCommand
import com.duro.kukie.team.domain.TeamRole
import java.util.UUID

data class UpdateTeamMemberRoleRequest(
    val role: TeamRole,
) {
    fun toCommand(teamId: UUID, targetUserId: UUID) = UpdateTeamMemberRoleCommand(
        teamId = teamId,
        targetUserId = targetUserId,
        role = role,
    )
}
