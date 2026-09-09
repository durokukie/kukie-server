package com.duro.kukie.team

import com.duro.kukie.team.domain.Team
import com.duro.kukie.team.domain.TeamMembership
import com.duro.kukie.team.domain.TeamRole
import java.util.UUID

object TeamFixture {

    const val DEFAULT_NAME = "DURO"

    fun team(name: String = DEFAULT_NAME): Team = Team(name)

    fun membership(
        teamId: UUID,
        userId: UUID,
        role: TeamRole = TeamRole.ADMIN,
    ): TeamMembership = TeamMembership(teamId = teamId, userId = userId, role = role)
}
