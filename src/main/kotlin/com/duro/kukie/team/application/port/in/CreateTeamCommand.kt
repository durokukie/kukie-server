package com.duro.kukie.team.application.port.`in`

import java.util.UUID

data class CreateTeamCommand(
    val userId: UUID,
    val name: String,
)
