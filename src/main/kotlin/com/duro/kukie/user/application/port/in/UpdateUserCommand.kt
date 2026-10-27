package com.duro.kukie.user.application.port.`in`

import java.util.UUID

data class UpdateUserCommand(
    val userId: UUID,
    val name: String,
)
