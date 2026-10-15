package com.duro.kukie.actionplan.application.port.`in`

import com.duro.kukie.actionplan.domain.PlanCall
import java.util.UUID

data class DecidePlanCommand(
    val planId: UUID,
    val approved: Boolean,
    val userId: UUID,
    val call: PlanCall?,
)
