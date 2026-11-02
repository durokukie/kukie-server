package com.duro.kukie.actionplan.domain

import java.util.UUID

interface UnsettledPlanRow {
    val planId: UUID
    val clusterId: UUID
    val runActive: Boolean
}
