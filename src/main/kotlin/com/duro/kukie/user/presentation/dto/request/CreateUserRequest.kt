package com.duro.kukie.user.presentation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateUserRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,

    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,

    @Schema(description = "이메일로 발송된 6자리 인증 코드 (유효시간 3분)", example = "123456")
    @field:NotBlank
    @field:Size(min = 6, max = 6)
    val verificationCode: String,
)
