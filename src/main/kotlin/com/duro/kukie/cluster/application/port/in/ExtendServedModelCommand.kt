package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class ExtendServedModelCommand(
    val clusterId: UUID,
    val userId: UUID,
    val name: String,
    val hours: Int,
)
