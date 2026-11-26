package com.duro.kukie.team.application.port.`in`

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class UpdateReportScheduleCommand(
    val teamId: UUID,
    val userId: UUID,
    val enabled: Boolean,
    val day: DayOfWeek,
    val time: LocalTime,
    val timeZone: String,
)
