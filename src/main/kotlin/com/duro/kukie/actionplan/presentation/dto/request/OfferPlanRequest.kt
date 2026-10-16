package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.OfferPlanCommand
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class OfferPlanRequest(
    @field:NotBlank
    val decisionGuidance: String,
) {
    fun toCommand(planId: UUID) = OfferPlanCommand(
        planId = planId,
        decisionGuidance = decisionGuidance.trim(),
    )
}
