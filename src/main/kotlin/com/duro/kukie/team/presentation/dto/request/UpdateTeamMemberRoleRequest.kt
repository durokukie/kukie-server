package com.duro.kukie.team.presentation.dto.request

import com.duro.kukie.team.domain.TeamRole
import jakarta.validation.constraints.NotNull

data class UpdateTeamMemberRoleRequest(
    @field:NotNull
    val role: TeamRole,
)
