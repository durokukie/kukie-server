package com.duro.kukie.actionplan.application.port.`in`

import java.util.UUID

data class OfferPlanCommand(
    val planId: UUID,
    val decisionGuidance: String,
)
