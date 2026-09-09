package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetMyTeamsService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    /** 내가 속한 팀과 각 팀에서의 내 역할. Agent 서버가 작업 공간을 그릴 때 부른다. */
    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): List<TeamResponse> {
        val memberships = teamMembershipRepository.findAllByUserId(userId)
        val teams = teamRepository.findAllById(memberships.map { it.teamId }).associateBy { it.id }

        return memberships.mapNotNull { membership ->
            teams[membership.teamId]?.let { TeamResponse.of(it, membership.role) }
        }
    }
}
