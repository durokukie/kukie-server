package com.duro.kukie.serving.presentation.dto.request

import com.duro.kukie.global.domain.Email
import com.duro.kukie.serving.application.port.`in`.InviteServingUserCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class InviteServingUserRequest(
    @field:NotBlank @field:jakarta.validation.constraints.Email @field:Size(max = 255)
    val email: String,
) {
    fun toCommand(clusterId: UUID, userId: UUID) = InviteServingUserCommand(clusterId, userId, Email(email))
}
