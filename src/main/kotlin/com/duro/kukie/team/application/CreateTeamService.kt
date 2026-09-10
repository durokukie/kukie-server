package com.duro.kukie.team.application

import com.duro.kukie.team.domain.Team
import com.duro.kukie.team.domain.TeamMembership
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.presentation.dto.request.CreateTeamRequest
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CreateTeamService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    /** 팀을 만든 사람은 자동으로 ADMIN이 된다 (제품기획서 02). */
    @Transactional
    operator fun invoke(userId: UUID, request: CreateTeamRequest): TeamResponse {
        val team = teamRepository.save(Team(request.name))
        teamMembershipRepository.save(
            TeamMembership(teamId = team.id, userId = userId, role = TeamRole.ADMIN),
        )

        return TeamResponse.of(team, TeamRole.ADMIN)
    }
}
