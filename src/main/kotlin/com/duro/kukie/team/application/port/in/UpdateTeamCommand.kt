package com.duro.kukie.team.application.port.`in`

import java.util.UUID

data class UpdateTeamCommand(
    val teamId: UUID,
    val userId: UUID,
    val name: String,
)
