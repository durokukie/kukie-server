package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.RecordPlanResultCommand
import java.util.UUID

data class RecordPlanResultRequest(
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int? = null,
    /** GitOps 길에서 PR 을 머지한 커밋. */
    val mergeCommit: String? = null,
    /** GitOps 길에서 머지 뒤 지켜본 ArgoCD sync 결과(모양은 에이전트가 정한다). */
    val syncResult: Map<String, Any?>? = null,
) {
    fun toCommand(planId: UUID) = RecordPlanResultCommand(
        planId = planId,
        success = success,
        stdout = stdout,
        stderr = stderr,
        exitCode = exitCode,
        mergeCommit = mergeCommit?.trim()?.ifEmpty { null },
        syncResult = syncResult,
    )
}
