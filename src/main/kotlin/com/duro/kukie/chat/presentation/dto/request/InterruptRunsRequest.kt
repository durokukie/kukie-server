package com.duro.kukie.chat.presentation.dto.request

import com.duro.kukie.chat.application.port.`in`.InterruptChatCommand
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class InterruptRunsRequest(
    @field:NotBlank
    val message: String,
) {
    fun toCommand(conversationId: UUID) = InterruptChatCommand(
        conversationId = conversationId,
        message = message,
    )
}
