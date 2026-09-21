package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import com.duro.kukie.team.exception.CannotRemoveSelfException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RemoveTeamMemberService(
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamPermission: TeamPermission,
) {

    /** 자기자신은 추방할 수 없다. 스스로 나가려는 경우 [LeaveTeamService]를 사용해야한다. */
    @Transactional
    operator fun invoke(teamId: UUID, targetUserId: UUID, userId: UUID) {
        if (targetUserId == userId) {
            throw CannotRemoveSelfException()
        }

        val target = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId, targetUserId)
        if (target.role == TeamRole.ADMIN) {
            teamPermission.requireNotLastAdmin(teamId)
        }

        teamMembershipRepository.delete(target)
    }
}
