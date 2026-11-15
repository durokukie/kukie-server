package com.duro.kukie.hosting.application.port.`in`

import java.util.UUID

data class ConnectHostingAppCommand(
    val userId: UUID,
    val app: String,
    val repository: String,
)
