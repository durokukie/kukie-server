package com.duro.kukie.user.presentation.dto.request

import com.duro.kukie.user.application.port.`in`.ChangePasswordCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class ChangePasswordRequest(
    @field:NotBlank
    val currentPassword: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Pattern(regexp = "^[!-~]+$", message = "비밀번호는 공백 없이 영문, 숫자, 특수문자만 사용할 수 있습니다.")
    val newPassword: String,
) {
    fun toCommand(userId: UUID) = ChangePasswordCommand(
        userId = userId,
        currentPassword = currentPassword,
        newPassword = newPassword,
    )
}
