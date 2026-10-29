package com.duro.kukie.chat.application.port.`in`

import java.util.UUID

data class UpdateRepoLinkStateCommand(
    val conversationId: UUID,
    val state: Map<String, Any?>,
)
