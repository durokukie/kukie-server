package com.duro.kukie.actionplan.domain

import java.util.UUID

data class VisibleActionPlan(
    val plan: ActionPlan,
    val clusterId: UUID,
    val ownerId: UUID,
)
