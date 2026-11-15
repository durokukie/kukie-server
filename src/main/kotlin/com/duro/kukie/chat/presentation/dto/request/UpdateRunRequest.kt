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

    /** 에이전트가 모델로 지은 제목. titleFrom 과 함께 올 때만 쓰고, 비었거나 못 쓰면 titleFrom 으로 짓는다. */
    val title: String? = null,
) {
    fun toCommand(runId: UUID) = UpdateRunCommand(
        runId = runId,
        status = status,
        responsePayload = responsePayload,
        appendAgentMessages = appendAgentMessages,
        usageSummary = usageSummary,
        titleFrom = titleFrom,
        title = title,
    )
}
