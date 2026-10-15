package com.duro.kukie.chat.presentation.dto.response

import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse

data class InterruptRunsResponse(
    val runs: List<RunResponse>,
    val closedPlans: List<PlanResponse>,
)
