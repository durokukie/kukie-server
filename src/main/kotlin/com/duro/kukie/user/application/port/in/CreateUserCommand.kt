package com.duro.kukie.user.application.port.`in`

import com.duro.kukie.global.domain.Email

data class CreateUserCommand(
    val name: String,
    val email: Email,
    val password: String,
    val verificationCode: String,
)
