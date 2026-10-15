package com.duro.kukie.chat.presentation.dto.response

import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.domain.RunTurn
import java.time.LocalDateTime
import java.util.UUID

data class TurnResponse(
    val id: UUID,
    val seq: Int,
    val kind: RunKind,
    val mode: String,
    val status: RunStatus,
    val inputText: String?,
    val payload: Map<String, Any?>?,
    val error: Map<String, Any?>?,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
) {
    companion object {
        fun from(turn: RunTurn): TurnResponse {
            val response = turn.responsePayload
            val failed = response?.get("kind") == ChatRun.ERROR_KIND

            return TurnResponse(
                id = turn.id,
                seq = turn.turnNo,
                kind = turn.kind,
                mode = turn.mode,
                status = turn.status,
                inputText = turn.inputText,
                payload = if (failed) null else response,
                error = if (failed) response.filterKeys { it != "kind" } else null,
                startedAt = turn.startedAt,
                finishedAt = turn.finishedAt,
            )
        }
    }
}
