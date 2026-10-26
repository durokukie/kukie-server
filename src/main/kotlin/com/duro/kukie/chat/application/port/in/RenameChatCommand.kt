package com.duro.kukie.chat.application.port.`in`

import java.util.UUID

data class RenameChatCommand(
    val conversationId: UUID,
    val userId: UUID,
    val title: String,
)
