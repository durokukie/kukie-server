package com.duro.kukie.actionplan.presentation.dto.request

import com.duro.kukie.actionplan.application.port.`in`.RecordPlanResultCommand
import java.util.UUID

data class RecordPlanResultRequest(
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int? = null,
) {
    fun toCommand(planId: UUID) = RecordPlanResultCommand(
        planId = planId,
        success = success,
        stdout = stdout,
        stderr = stderr,
        exitCode = exitCode,
    )
}
