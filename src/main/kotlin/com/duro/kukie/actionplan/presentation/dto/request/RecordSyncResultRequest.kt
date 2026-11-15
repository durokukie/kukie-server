package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.RecordSyncResultCommand
import java.util.UUID

data class RecordSyncResultRequest(
    /** 머지 뒤 지켜본 ArgoCD sync 결과(모양은 에이전트가 정한다). */
    val syncResult: Map<String, Any?>,
) {
    fun toCommand(planId: UUID) = RecordSyncResultCommand(
        planId = planId,
        syncResult = syncResult,
    )
}
