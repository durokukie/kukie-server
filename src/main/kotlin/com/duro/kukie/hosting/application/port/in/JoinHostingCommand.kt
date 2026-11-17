package com.duro.kukie.hosting.application.port.`in`

import java.util.UUID

data class JoinHostingCommand(
    val userId: UUID,
    val agreed: Boolean,
)
