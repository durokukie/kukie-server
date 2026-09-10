package com.duro.kukie.team.presentation.dto.response

import com.duro.kukie.team.domain.Team
import com.duro.kukie.team.domain.TeamRole
import java.util.UUID

/** 팀 하나와 그 팀에서의 내 역할. 목록에서도 상세에서도 이 모양을 쓴다. */
data class TeamResponse(
    val id: UUID,
    val name: String,
    val role: TeamRole,
) {
    companion object {
        fun of(team: Team, role: TeamRole) = TeamResponse(
            id = team.id,
            name = team.name,
            role = role,
        )
    }
}
