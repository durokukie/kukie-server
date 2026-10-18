package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class RegisterEksClusterCommand(
    val userId: UUID,
    val teamId: UUID,
    val accountId: String,
    val region: String,
    val clusterName: String,
    val namespace: String,
    val name: String,
)
