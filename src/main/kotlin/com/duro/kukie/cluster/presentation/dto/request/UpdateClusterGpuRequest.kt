package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.UpdateClusterGpuCommand
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class UpdateClusterGpuRequest(
    val hasGpu: Boolean,

    @field:NotBlank
    val fingerprint: String,
) {
    fun toCommand(clusterId: UUID) = UpdateClusterGpuCommand(
        clusterId = clusterId,
        hasGpu = hasGpu,
        fingerprint = fingerprint,
    )
}
