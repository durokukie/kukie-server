package com.duro.kukie.auth.presentation.dto.request

import jakarta.validation.constraints.NotBlank

data class LogInRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String,
)
