package com.duro.kukie.chat.domain

import java.time.LocalDateTime
import java.util.UUID

data class RunTurn(
    val id: UUID,
    val turnNo: Int,
    val kind: RunKind,
    val mode: String,
    val status: RunStatus,
    val inputText: String?,
    val responsePayload: Map<String, Any?>?,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
)
