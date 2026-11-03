package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class UpdateGpuNodeGroupCommand(
    val clusterId: UUID,
    val userId: UUID,
    val name: String,
    val desired: Int,
)
