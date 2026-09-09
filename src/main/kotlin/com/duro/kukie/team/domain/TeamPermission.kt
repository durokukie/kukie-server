package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.NotTeamAdminException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * "이 사용자가 이 팀에서 무엇을 할 수 있나" 를 판단하는 한 곳.
 * 팀 API마다 흩어 두면 하나를 빠뜨리기 쉬워 모아 둔다. 모든 서비스가 첫 줄에서 부른다.
 */
@Component
class TeamPermission(
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    /** 멤버가 아니면 403. 멤버면 그 역할을 돌려준다. */
    fun requireMember(teamId: UUID, userId: UUID): TeamMembership =
        teamMembershipRepository.findMembershipOrThrow(teamId, userId)

    /** Admin이 아니면 403. 팀·클러스터 관리 작업 앞에 둔다 (제품기획서 02·03). */
    fun requireAdmin(teamId: UUID, userId: UUID): TeamMembership {
        val membership = requireMember(teamId, userId)
        if (membership.role.isAdmin.not()) {
            throw NotTeamAdminException()
        }

        return membership
    }
}
