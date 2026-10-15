package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class RegisterClusterCommand(
    val userId: UUID,
    val teamId: UUID,
    val name: String,
    val kubeconfig: String,
    val context: String?,
    val namespace: String?,
)
