package com.duro.kukie.actionplan.presentation.dto.response

import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.PlanFailureReason
import com.duro.kukie.actionplan.domain.PlanRisk
import com.duro.kukie.actionplan.domain.PlanStatus
import java.time.LocalDateTime
import java.util.UUID

data class PlanResponse(
    val id: UUID,
    val code: String,
    val runId: UUID,
    val toolCallId: String,
    val toolName: String,
    val status: PlanStatus,
    val risk: PlanRisk,
    val payload: Map<String, Any?>,
    val requestHash: String,
    val decision: Map<String, Any?>?,
    val executionResult: Map<String, Any?>?,
    val failureReason: PlanFailureReason?,
    val appliedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(plan: ActionPlan) = PlanResponse(
            id = plan.id,
            code = plan.code,
            runId = plan.runId,
            toolCallId = plan.toolCallId,
            toolName = plan.toolName,
            status = plan.status,
            risk = plan.risk,
            payload = plan.payload,
            requestHash = plan.requestHash,
            decision = plan.decision,
            executionResult = plan.executionResult,
            failureReason = plan.failureReason,
            appliedAt = plan.appliedAt,
            createdAt = plan.createdAt,
            updatedAt = plan.updatedAt,
        )
    }
}
