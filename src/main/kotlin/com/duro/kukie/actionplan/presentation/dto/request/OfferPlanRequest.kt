package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.OfferPlanCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class OfferPlanRequest(
    @field:NotBlank
    val decisionGuidance: String,
    /** GitOps 길에서 승인 전에 연 PR. kubectl 길이면 null. */
    @field:Valid
    val pullRequest: PullRequestRequest? = null,
) {
    fun toCommand(planId: UUID) = OfferPlanCommand(
        planId = planId,
        decisionGuidance = decisionGuidance.trim(),
        pullRequest = pullRequest?.toDomain(),
    )
}
