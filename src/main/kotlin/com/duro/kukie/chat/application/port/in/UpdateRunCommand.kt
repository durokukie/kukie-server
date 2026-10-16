package com.duro.kukie.chat.application.port.`in`

import com.duro.kukie.chat.domain.RunStatus
import java.util.UUID

data class UpdateRunCommand(
    val runId: UUID,
    val status: RunStatus?,
    val responsePayload: Map<String, Any?>?,
    val appendAgentMessages: List<Any?>?,
    val usageSummary: Map<String, Any?>?,
    val currentMode: String?,
    val titleFrom: String?,
)
