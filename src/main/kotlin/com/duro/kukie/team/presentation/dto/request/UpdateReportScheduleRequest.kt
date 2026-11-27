package com.duro.kukie.team.presentation.dto.request

import com.duro.kukie.team.application.port.`in`.UpdateReportScheduleCommand
import jakarta.validation.constraints.NotBlank
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class UpdateReportScheduleRequest(
    /** 끄면 자동으로 만들지 않는다. 요일·시각은 그대로 저장한다 */
    val enabled: Boolean,

    val day: DayOfWeek,

    /** timeZone 의 시각(HH:mm). 초는 버린다 */
    val time: LocalTime,

    /** IANA 시간대 이름 (예: Asia/Seoul) */
    @field:NotBlank
    val timeZone: String,
) {
    fun toCommand(teamId: UUID, userId: UUID) = UpdateReportScheduleCommand(
        teamId = teamId,
        userId = userId,
        enabled = enabled,
        day = day,
        time = time,
        timeZone = timeZone,
    )
}
