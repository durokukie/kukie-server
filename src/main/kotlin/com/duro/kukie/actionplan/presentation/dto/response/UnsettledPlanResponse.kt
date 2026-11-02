package com.duro.kukie.actionplan.presentation.dto.response

import java.util.UUID

data class UnsettledPlanResponse(
    val plan: PlanResponse,
    val clusterId: UUID,
    val runActive: Boolean,
)
