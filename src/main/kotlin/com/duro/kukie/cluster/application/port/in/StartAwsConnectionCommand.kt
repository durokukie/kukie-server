package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class StartAwsConnectionCommand(
    val userId: UUID,
    val teamId: UUID,
)
