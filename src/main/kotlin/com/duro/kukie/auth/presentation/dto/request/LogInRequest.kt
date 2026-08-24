package com.duro.kukie.auth.presentation.dto.request

import jakarta.validation.constraints.NotBlank

data class LogInRequest(
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,
)
