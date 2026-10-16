package com.duro.kukie.chat.application.port.`in`

import java.util.UUID

data class InterruptChatCommand(
    val conversationId: UUID,
    val message: String,
)
