package com.duro.kukie.cluster.application.port.`in`

import com.duro.kukie.cluster.domain.ClusterStatus
import java.time.LocalDateTime
import java.util.UUID

data class UpdateClusterStatusCommand(
    val clusterId: UUID,
    val status: ClusterStatus,
    val checkedAt: LocalDateTime,
)
