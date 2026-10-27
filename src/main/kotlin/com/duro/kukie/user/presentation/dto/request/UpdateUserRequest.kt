package com.duro.kukie.user.presentation.dto.request

import com.duro.kukie.user.application.port.`in`.UpdateUserCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateUserRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,
) {
    fun toCommand(userId: UUID) = UpdateUserCommand(
        userId = userId,
        name = name,
    )
}
