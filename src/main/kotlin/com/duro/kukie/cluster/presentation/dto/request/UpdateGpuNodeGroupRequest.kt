package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.UpdateGpuNodeGroupCommand
import java.util.UUID

data class UpdateGpuNodeGroupRequest(
    val desired: Int,
) {
    fun toCommand(clusterId: UUID, userId: UUID, name: String) = UpdateGpuNodeGroupCommand(
        clusterId = clusterId,
        userId = userId,
        name = name,
        desired = desired,
    )
}
