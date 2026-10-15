package com.duro.kukie.actionplan.application.port.`in`

import com.duro.kukie.actionplan.domain.DryRunStatus
import java.util.UUID

data class RecordDryRunCommand(
    val planId: UUID,
    val status: DryRunStatus,
    val stdout: String,
    val stderr: String,
)
