package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class ListEksNamespacesCommand(
    val userId: UUID,
    val connectionId: UUID,
    val accountId: String,
    val region: String,
    val clusterName: String,
)
