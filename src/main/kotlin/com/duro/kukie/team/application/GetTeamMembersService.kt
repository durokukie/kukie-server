package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.presentation.dto.response.TeamMemberResponse
import com.duro.kukie.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetTeamMembersService(
    private val teamMembershipRepository: TeamMembershipRepository,
    private val userRepository: UserRepository,
    private val teamPermission: TeamPermission,
) {

    /** 팀 멤버 목록. 같은 팀 구성원만 볼 수 있다. */
    @Transactional(readOnly = true)
    operator fun invoke(teamId: UUID, userId: UUID): List<TeamMemberResponse> {
        teamPermission.requireMember(teamId, userId)

        val memberships = teamMembershipRepository.findAllByTeamId(teamId)
        val users = userRepository.findAllById(memberships.map { it.userId }).associateBy { it.id }

        return memberships.mapNotNull { membership ->
            users[membership.userId]?.let { TeamMemberResponse.of(it, membership) }
        }
    }
}
