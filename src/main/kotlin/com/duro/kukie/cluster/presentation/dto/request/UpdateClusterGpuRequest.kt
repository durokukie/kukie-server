package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.UpdateClusterGpuCommand
import java.util.UUID

data class UpdateClusterGpuRequest(
    val hasGpu: Boolean,
) {
    fun toCommand(clusterId: UUID) = UpdateClusterGpuCommand(
        clusterId = clusterId,
        hasGpu = hasGpu,
    )
}
