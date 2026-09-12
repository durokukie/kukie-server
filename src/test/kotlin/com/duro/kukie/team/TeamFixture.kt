package com.duro.kukie.team

import com.duro.kukie.team.domain.Team
import com.duro.kukie.team.domain.TeamInvitation
import com.duro.kukie.team.domain.TeamMembership
import com.duro.kukie.team.domain.TeamRole
import java.time.LocalDateTime
import java.util.UUID

object TeamFixture {

    const val DEFAULT_NAME = "DURO"

    fun team(name: String = DEFAULT_NAME): Team = Team(name)

    fun membership(
        teamId: UUID,
        userId: UUID,
        role: TeamRole = TeamRole.ADMIN,
    ): TeamMembership = TeamMembership(teamId = teamId, userId = userId, role = role)

    fun invitation(
        teamId: UUID,
        email: String,
        invitedBy: UUID,
        expiresAt: LocalDateTime = LocalDateTime.now().plus(TeamInvitation.VALIDITY),
    ): TeamInvitation = TeamInvitation(teamId = teamId, email = email, invitedBy = invitedBy, expiresAt = expiresAt)
}
