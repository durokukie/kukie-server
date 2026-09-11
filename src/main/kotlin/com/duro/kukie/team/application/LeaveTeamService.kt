package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class LeaveTeamService(
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamPermission: TeamPermission,
) {

    @Transactional
    operator fun invoke(teamId: UUID, userId: UUID) {
        val membership = teamPermission.requireMember(teamId, userId)
        if (membership.role.isAdmin) {
            teamPermission.requireNotLastAdmin(teamId)
        }

        teamMembershipRepository.delete(membership)
    }
}
