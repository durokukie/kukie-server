package com.duro.kukie.auth.presentation.dto.request

import com.duro.kukie.auth.application.port.`in`.LogInCommand
import com.duro.kukie.global.domain.Email
import jakarta.validation.constraints.NotBlank

data class LogInRequest(
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,
) {
    fun toCommand() = LogInCommand(
        email = Email(email),
        password = password,
    )
}
