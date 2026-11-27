package com.duro.kukie.team.presentation.dto.response

import com.duro.kukie.team.domain.Team
import com.duro.kukie.team.domain.TeamRole
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** 팀 하나와 그 팀에서의 내 역할. 목록에서도 상세에서도 이 모양을 쓴다. */
data class TeamResponse(
    val id: UUID,
    val name: String,
    val role: TeamRole,
    val reportSchedule: ReportSchedule,
) {
    /** 주간 보고서를 자동으로 만드는지와 그 요일·시각. time 은 timeZone 의 시각이고 nextAt 은 UTC 다. 꺼져 있으면 nextAt 은 null 이다. */
    data class ReportSchedule(
        val enabled: Boolean,
        val day: DayOfWeek,
        val time: LocalTime,
        val timeZone: String,
        val nextAt: LocalDateTime?,
    )

    companion object {
        fun of(team: Team, role: TeamRole) = TeamResponse(
            id = team.id,
            name = team.name,
            role = role,
            reportSchedule = ReportSchedule(
                enabled = team.reportEnabled,
                day = team.reportDay,
                time = team.reportTime,
                timeZone = team.reportZone,
                nextAt = team.reportNextAt.takeIf { team.reportEnabled },
            ),
        )
    }
}
