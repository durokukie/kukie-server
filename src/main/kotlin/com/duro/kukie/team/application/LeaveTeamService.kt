package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class LeaveTeamService(
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamPermission: TeamPermission,
) {

    @Transactional
    operator fun invoke(teamId: UUID, userId: UUID) {
        val membership = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId, userId)
        if (membership.role == TeamRole.ADMIN) {
            teamPermission.requireNotLastAdmin(teamId)
        }

        teamMembershipRepository.delete(membership)
    }
}
