package com.duro.kukie.chat.presentation.dto.response

import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.RunStatus
import java.time.LocalDateTime
import java.util.UUID

data class RunResponse(
    val id: UUID,
    val conversationId: UUID,
    val requestId: String,
    val seq: Int,
    val kind: RunKind,
    val mode: String,
    val status: RunStatus,
    val inputText: String?,
    val responsePayload: Map<String, Any?>?,
    val agentMessages: List<Any?>?,
    val usageSummary: Map<String, Any?>?,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
) {
    companion object {
        fun from(run: ChatRun) = RunResponse(
            id = run.id,
            conversationId = run.sessionId,
            requestId = run.requestId,
            seq = run.turnNo,
            kind = run.kind,
            mode = run.mode,
            status = run.status,
            inputText = run.inputText,
            responsePayload = run.responsePayload,
            agentMessages = run.agentMessages,
            usageSummary = run.usageSummary,
            startedAt = run.startedAt,
            finishedAt = run.finishedAt,
        )
    }
}
