package com.duro.kukie.chat.application

import com.duro.kukie.chat.domain.ChatSession
import java.util.UUID

data class ChatAccess(
    val session: ChatSession,
    val teamId: UUID,
)
