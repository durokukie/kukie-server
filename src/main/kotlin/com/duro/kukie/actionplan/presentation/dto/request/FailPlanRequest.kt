package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.FailPlanCommand
import com.duro.kukie.actionplan.domain.PlanFailureReason
import java.util.UUID

data class FailPlanRequest(
    val reason: PlanFailureReason,
) {
    fun toCommand(planId: UUID) = FailPlanCommand(
        planId = planId,
        reason = reason,
    )
}
