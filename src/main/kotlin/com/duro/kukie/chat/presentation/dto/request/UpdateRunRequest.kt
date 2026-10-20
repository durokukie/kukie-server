package com.duro.kukie.chat.presentation.dto.request

import com.duro.kukie.chat.application.port.`in`.UpdateRunCommand
import com.duro.kukie.chat.domain.RunStatus
import java.util.UUID

data class UpdateRunRequest(
    val status: RunStatus? = null,

    val responsePayload: Map<String, Any?>? = null,

    val appendAgentMessages: List<Any?>? = null,

    val usageSummary: Map<String, Any?>? = null,

    val titleFrom: String? = null,
) {
    fun toCommand(runId: UUID) = UpdateRunCommand(
        runId = runId,
        status = status,
        responsePayload = responsePayload,
        appendAgentMessages = appendAgentMessages,
        usageSummary = usageSummary,
        titleFrom = titleFrom,
    )
}
