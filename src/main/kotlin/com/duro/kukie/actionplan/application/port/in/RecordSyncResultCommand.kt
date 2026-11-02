package com.duro.kukie.actionplan.application.port.`in`

import java.util.UUID

data class RecordSyncResultCommand(
    val planId: UUID,
    val syncResult: Map<String, Any?>,
)
