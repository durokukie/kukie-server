package com.duro.kukie.actionplan.application.port.`in`

import com.duro.kukie.actionplan.domain.PlanPullRequest
import java.util.UUID

data class OfferPlanCommand(
    val planId: UUID,
    val decisionGuidance: String,
    val pullRequest: PlanPullRequest?,
)
