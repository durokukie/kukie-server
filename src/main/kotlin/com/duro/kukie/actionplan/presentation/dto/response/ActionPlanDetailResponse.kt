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
        )
    }
}
