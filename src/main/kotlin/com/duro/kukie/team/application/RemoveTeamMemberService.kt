package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import com.duro.kukie.team.exception.CannotRemoveSelfException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class RemoveTeamMemberService(
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamPermission: TeamPermission,
) {

    /** 추방은 Admin만, 대상은 남만. 자기 자신은 [LeaveTeamService]로 나간다. */
    @Transactional
    operator fun invoke(teamId: UUID, targetUserId: UUID, userId: UUID) {
        teamPermission.requireAdmin(teamId, userId)
        if (targetUserId == userId) {
            throw CannotRemoveSelfException()
        }

        val target = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId, targetUserId)
        if (target.role.isAdmin) {
            teamPermission.requireNotLastAdmin(teamId)
        }

        teamMembershipRepository.delete(target)
    }
}
