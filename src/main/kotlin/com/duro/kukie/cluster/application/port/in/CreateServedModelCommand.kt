package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class CreateServedModelCommand(
    val clusterId: UUID,
    val userId: UUID,
    val model: String,
    val storageGi: Int,
    val gpus: Int,
)
