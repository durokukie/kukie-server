package com.duro.kukie.hosting.application.port.`in`

import java.util.UUID

data class CreateHostingAgentDeployCommand(
    val userId: UUID,
    val name: String,
    val bytes: Long,
)
