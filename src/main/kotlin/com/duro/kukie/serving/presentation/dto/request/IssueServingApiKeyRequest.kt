package com.duro.kukie.serving.presentation.dto.request

import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

class IssueServingApiKeyRequest(
    @field:NotBlank @field:Size(max = 80) val name: String,
    val expiresInDays: Int?,
) {
    fun toCommand(accessId: UUID, userId: UUID) = IssueServingApiKeyCommand(accessId, userId, name.trim(), expiresInDays ?: 90)
}
