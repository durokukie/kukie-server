package com.duro.kukie.user.application.port.`in`

import java.util.UUID

data class ChangePasswordCommand(
    val userId: UUID,
    val currentPassword: String,
    val newPassword: String,
)
