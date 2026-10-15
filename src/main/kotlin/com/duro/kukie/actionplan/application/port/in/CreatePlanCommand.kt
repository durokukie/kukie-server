package com.duro.kukie.actionplan.application.port.`in`

import com.duro.kukie.actionplan.domain.PlanRisk
import java.util.UUID

data class CreatePlanCommand(
    val runId: UUID,
    val toolCallId: String,
    val toolName: String,
    val risk: PlanRisk,
    val requestHash: String,
    val payload: Map<String, Any?>,
)
