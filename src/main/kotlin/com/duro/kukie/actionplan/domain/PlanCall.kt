package com.duro.kukie.actionplan.domain

data class PlanCall(
    val tool: String,
    val args: Map<String, Any?>,
    val command: List<Any?>,
    val risk: PlanRisk,
    val target: Map<String, Any?>,
)
