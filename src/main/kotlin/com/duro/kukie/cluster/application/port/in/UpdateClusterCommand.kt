package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class UpdateClusterCommand(
    val clusterId: UUID,
    val userId: UUID,
    val name: String?,
    val namespace: String?,
    val kubeconfig: String?,
    val context: String?,
)
