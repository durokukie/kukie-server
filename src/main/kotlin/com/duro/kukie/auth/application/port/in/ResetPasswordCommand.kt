package com.duro.kukie.auth.application.port.`in`

import com.duro.kukie.global.domain.Email

data class ResetPasswordCommand(
    val email: Email,
    val verificationCode: String,
    val newPassword: String,
)
