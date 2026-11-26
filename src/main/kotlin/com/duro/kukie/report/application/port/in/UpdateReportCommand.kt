package com.duro.kukie.report.application.port.`in`

import java.util.UUID

data class UpdateReportCommand(
    val reportId: UUID,
    val userId: UUID,
    val title: String,
    val content: String,
    val version: Int,
)
