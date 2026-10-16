package com.duro.kukie.actionplan.domain

import java.util.UUID

interface RunPlanCount {
    val runId: UUID
    val plans: Long
}
