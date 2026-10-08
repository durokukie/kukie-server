package com.duro.kukie.auth.application.port.`in`

import com.duro.kukie.global.domain.Email

data class LogInCommand(
    val email: Email,
    val password: String,
)
