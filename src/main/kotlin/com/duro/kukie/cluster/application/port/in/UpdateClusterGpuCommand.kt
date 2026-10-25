package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class UpdateClusterGpuCommand(
    val clusterId: UUID,
    val hasGpu: Boolean,
    val fingerprint: String,
)
