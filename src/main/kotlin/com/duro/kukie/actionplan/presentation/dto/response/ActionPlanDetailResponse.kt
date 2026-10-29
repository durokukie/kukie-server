package com.duro.kukie.actionplan.presentation.dto.response

import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.PlanFailureReason
import com.duro.kukie.actionplan.domain.PlanRisk
import com.duro.kukie.actionplan.domain.PlanStatus
import java.time.LocalDateTime
import java.util.UUID

data class ActionPlanDetailResponse(
    val id: UUID,
    val code: String,
    val runId: UUID,
    val status: PlanStatus,
    val risk: PlanRisk,
    val failureReason: PlanFailureReason?,
    val decision: Map<String, Any?>?,
    val executionResult: Map<String, Any?>?,
    val appliedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val markdown: String,
    /** 아래는 payload 를 항목별로 꺼낸 것 — 화면이 계획서(markdown) 원문 대신 나눠 그린다 */
    val title: String,
    val toolName: String,
    val command: List<Any?>,
    val args: Map<String, Any?>,
    val target: Map<String, Any?>,
    val skill: String?,
    val expectedEffects: List<String>,
    val sideEffects: List<String>,
    val dryRunResult: Map<String, Any?>?,
    val decisionGuidance: String?,
    /** ArgoCD 가 관리하는 리소스면 에이전트가 붙인 경고(summary·level·warning·applications·resources). 아니면 null */
    val argocd: Map<String, Any?>?,
    /** 아래 넷은 GitOps 길(매니페스트 레포 PR)일 때만 있다 */
    val pullRequestNumber: Long?,
    val pullRequestUrl: String?,
    val mergeCommit: String?,
    val syncResult: Map<String, Any?>?,
) {
    companion object {
        fun of(plan: ActionPlan, markdown: String) = ActionPlanDetailResponse(
            id = plan.id,
            code = plan.code,
            runId = plan.runId,
            status = plan.status,
            risk = plan.risk,
            failureReason = plan.failureReason,
            decision = plan.decision,
            executionResult = plan.executionResult,
            appliedAt = plan.appliedAt,
            createdAt = plan.createdAt,
            updatedAt = plan.updatedAt,
            markdown = markdown,
            title = plan.title,
            toolName = plan.toolName,
            command = (plan.payload["command"] as? List<*>).orEmpty().toList(),
            args = stringKeyed(plan.payload["args"]).orEmpty(),
            target = stringKeyed(plan.payload["target"]).orEmpty(),
            skill = (plan.payload["skill"] as? String)?.ifBlank { null },
            expectedEffects = strings(plan.payload["expected_effects"]),
            sideEffects = strings(plan.payload["side_effects"]),
            dryRunResult = stringKeyed(plan.payload[ActionPlan.DRY_RUN_RESULT]),
            decisionGuidance = (plan.payload[ActionPlan.DECISION_GUIDANCE] as? String)?.ifBlank { null },
            argocd = stringKeyed(plan.payload["argocd"]),
            pullRequestNumber = (pullRequest(plan)?.get("number") as? Number)?.toLong(),
            pullRequestUrl = (pullRequest(plan)?.get("url") as? String)?.ifBlank { null },
            mergeCommit = (plan.executionResult?.get(ActionPlan.MERGE_COMMIT) as? String)?.ifBlank { null },
            syncResult = stringKeyed(plan.executionResult?.get(ActionPlan.SYNC_RESULT)),
        )

        private fun pullRequest(plan: ActionPlan): Map<*, *>? = plan.payload[ActionPlan.PULL_REQUEST] as? Map<*, *>

        private fun stringKeyed(value: Any?): Map<String, Any?>? =
            (value as? Map<*, *>)?.entries?.associate { (key, item) -> key.toString() to item }

        private fun strings(value: Any?): List<String> =
            (value as? List<*>).orEmpty().mapNotNull { it?.toString() }
    }
}
