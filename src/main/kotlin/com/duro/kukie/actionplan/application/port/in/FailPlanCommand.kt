package com.duro.kukie.actionplan.application.port.`in`

import com.duro.kukie.actionplan.domain.PlanFailureReason
import java.util.UUID

data class FailPlanCommand(
    val planId: UUID,
    val reason: PlanFailureReason,
)
