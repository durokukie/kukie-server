package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.RecordDryRunCommand
import com.duro.kukie.actionplan.domain.DryRunStatus
import java.util.UUID

data class RecordDryRunRequest(
    val status: DryRunStatus,
    val stdout: String,
    val stderr: String,
) {
    fun toCommand(planId: UUID) = RecordDryRunCommand(
        planId = planId,
        status = status,
        stdout = stdout,
        stderr = stderr,
    )
}
