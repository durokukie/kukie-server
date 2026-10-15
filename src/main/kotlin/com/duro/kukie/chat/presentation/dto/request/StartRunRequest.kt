package com.duro.kukie.chat.presentation.dto.request

import com.duro.kukie.chat.application.port.`in`.StartRunCommand
import com.duro.kukie.chat.domain.RunKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class StartRunRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val requestId: String,

    val kind: RunKind,

    @field:NotBlank
    @field:Size(max = 20)
    val mode: String,

    val inputText: String? = null,
) {
    fun toCommand(conversationId: UUID, userId: UUID) = StartRunCommand(
        conversationId = conversationId,
        userId = userId,
        requestId = requestId,
        kind = kind,
        mode = mode,
        inputText = inputText,
    )
}
