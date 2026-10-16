package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.CreatePlanCommand
import com.duro.kukie.actionplan.domain.PlanRisk
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreatePlanRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val toolCallId: String,

    @field:NotBlank
    @field:Size(max = 50)
    val toolName: String,

    val risk: PlanRisk,

    @field:NotBlank
    @field:Size(max = 64)
    val requestHash: String,

    val payload: Map<String, Any?>,
) {
    fun toCommand(runId: UUID) = CreatePlanCommand(
        runId = runId,
        toolCallId = toolCallId,
        toolName = toolName,
        risk = risk,
        requestHash = requestHash,
        payload = payload,
    )
}
