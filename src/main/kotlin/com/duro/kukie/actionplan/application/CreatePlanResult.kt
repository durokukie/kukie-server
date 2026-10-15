package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse

data class CreatePlanResult(
    val plan: PlanResponse,
    val created: Boolean,
)
