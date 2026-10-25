package com.duro.kukie.chat.application.port.`in`

import com.duro.kukie.chat.domain.ChatVisibility
import java.util.UUID

data class CreateChatCommand(
    val userId: UUID,
    val clusterId: UUID?,
    val teamId: UUID?,
    val title: String,
    val visibility: ChatVisibility,
)
