package com.duro.kukie.actionplan.application.port.`in`

import java.util.UUID

data class RecordPlanResultCommand(
    val planId: UUID,
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val mergeCommit: String?,
    val syncResult: Map<String, Any?>?,
)
