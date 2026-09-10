package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RemoveTeamMemberService(
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamPermission: TeamPermission,
) {

    /** 멤버 제거는 Admin만. 마지막 Admin은 제거할 수 없다. */
    @Transactional
    operator fun invoke(teamId: UUID, targetUserId: UUID, userId: UUID) {
        teamPermission.requireAdmin(teamId, userId)

        val target = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId, targetUserId)
        if (target.role.isAdmin) {
            teamPermission.requireNotLastAdmin(teamId)
        }

        teamMembershipRepository.delete(target)
    }
}
