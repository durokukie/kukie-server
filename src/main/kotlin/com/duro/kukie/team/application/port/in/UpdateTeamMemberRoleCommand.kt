package com.duro.kukie.team.application.port.`in`

import com.duro.kukie.team.domain.TeamRole
import java.util.UUID

data class UpdateTeamMemberRoleCommand(
    val teamId: UUID,
    val targetUserId: UUID,
    val role: TeamRole,
)
