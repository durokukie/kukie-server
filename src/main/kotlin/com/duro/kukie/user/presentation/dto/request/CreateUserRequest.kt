package com.duro.kukie.user.presentation.dto.request

import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.application.port.`in`.CreateUserCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateUserRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,

    @field:NotBlank
    @field:jakarta.validation.constraints.Email
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Pattern(regexp = "^[!-~]+$", message = "비밀번호는 공백 없이 영문, 숫자, 특수문자만 사용할 수 있습니다.")
    val password: String,

    @Schema(description = "이메일로 발송된 6자리 인증 코드 (유효시간 3분)", example = "123456")
    @field:NotBlank
    @field:Size(min = 6, max = 6)
    val verificationCode: String,
) {
    fun toCommand() = CreateUserCommand(
        name = name,
        email = Email(email),
        password = password,
        verificationCode = verificationCode,
    )
}
