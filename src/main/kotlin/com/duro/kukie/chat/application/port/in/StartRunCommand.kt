package com.duro.kukie.chat.application.port.`in`

import com.duro.kukie.chat.domain.RunKind
import java.util.UUID

data class StartRunCommand(
    val conversationId: UUID,
    val userId: UUID,
    val requestId: String,
    val kind: RunKind,
    val mode: String,
    val inputText: String?,
)
