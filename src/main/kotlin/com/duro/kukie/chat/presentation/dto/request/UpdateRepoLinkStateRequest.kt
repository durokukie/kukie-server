package com.duro.kukie.chat.presentation.dto.request

import com.duro.kukie.chat.application.port.`in`.UpdateRepoLinkStateCommand
import java.util.UUID

data class UpdateRepoLinkStateRequest(
    val state: Map<String, Any?>,
) {
    fun toCommand(conversationId: UUID) = UpdateRepoLinkStateCommand(conversationId = conversationId, state = state)
}
