package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.domain.PlanCall
import com.duro.kukie.actionplan.domain.PlanRisk

data class PlanCallRequest(
    val tool: String,
    val args: Map<String, Any?>,
    val command: List<Any?>,
    val risk: PlanRisk,
    val target: Map<String, Any?>,
) {
    fun toPlanCall() = PlanCall(
        tool = tool,
        args = args,
        command = command,
        risk = risk,
        target = target,
    )
}
