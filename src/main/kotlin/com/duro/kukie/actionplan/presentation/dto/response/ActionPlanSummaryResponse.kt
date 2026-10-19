package com.duro.kukie.actionplan.presentation.dto.response

import com.duro.kukie.actionplan.domain.PlanRisk
import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.actionplan.domain.VisibleActionPlan
import java.time.LocalDateTime
import java.util.UUID

data class ActionPlanSummaryResponse(
    val id: UUID,
    val code: String,
    val clusterId: UUID,
    val title: String,
    val status: PlanStatus,
    val running: Boolean,
    val risk: PlanRisk,
    val requestedBy: UUID,
    val requestedByName: String,
    val updatedAt: LocalDateTime,
    val approvals: Long,
) {
    companion object {
        fun of(row: VisibleActionPlan, approvals: Long): ActionPlanSummaryResponse {
            val plan = row.plan
            return ActionPlanSummaryResponse(
                id = plan.id,
                code = plan.code,
                clusterId = row.clusterId,
                title = plan.title,
                status = plan.status,
                running = plan.status == PlanStatus.EXECUTING,
                risk = plan.risk,
                requestedBy = row.ownerId,
                requestedByName = row.ownerName,
                updatedAt = plan.updatedAt,
                approvals = approvals,
            )
        }
    }
}
