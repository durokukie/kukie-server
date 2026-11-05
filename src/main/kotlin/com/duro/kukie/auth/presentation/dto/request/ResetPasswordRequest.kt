package com.duro.kukie.auth.presentation.dto.request

import com.duro.kukie.auth.application.port.`in`.ResetPasswordCommand
import com.duro.kukie.global.domain.Email
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(
    @field:NotBlank
    @field:jakarta.validation.constraints.Email
    @field:Size(max = 255)
    val email: String,

    @Schema(description = "이메일로 발송된 6자리 재설정 코드 (유효시간 3분)", example = "123456")
    @field:NotBlank
    @field:Size(min = 6, max = 6)
    val verificationCode: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Pattern(regexp = "^[!-~]+$", message = "비밀번호는 공백 없이 영문, 숫자, 특수문자만 사용할 수 있습니다.")
    val newPassword: String,
) {
    fun toCommand() = ResetPasswordCommand(
        email = Email(email),
        verificationCode = verificationCode,
        newPassword = newPassword,
    )
}
