package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.UpdateClusterStatusCommand
import com.duro.kukie.cluster.domain.ClusterStatus
import java.time.LocalDateTime
import java.util.UUID

data class UpdateClusterStatusRequest(
    val status: ClusterStatus,
    val checkedAt: LocalDateTime,
) {
    fun toCommand(clusterId: UUID) = UpdateClusterStatusCommand(
        clusterId = clusterId,
        status = status,
        checkedAt = checkedAt,
    )
}
