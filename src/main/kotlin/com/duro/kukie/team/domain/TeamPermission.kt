package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.AdminRequiredException
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
        teamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId, userId)

    /** Admin이 아니면 403. 팀·클러스터 관리 작업 앞에 둔다 (제품기획서 02·03). */
    fun requireAdmin(teamId: UUID, userId: UUID): TeamMembership {
        val membership = requireMember(teamId, userId)
        if (membership.role.isAdmin.not()) {
            throw NotTeamAdminException()
        }

        return membership
    }

    /**
     * ADMIN 을 하나 줄이는 변경 앞에서 부른다. 마지막 한 명이면 409 — 관리자가 없는 팀은 아무도
     * 관리할 수 없고 삭제조차 못 한다 (제품기획서 02 §3).
     *
     * 세지 않고 **잠그고 읽는다.** 관리자 A·B 뿐인 팀에서 서로를 동시에 제거·강등하면, 세기만 해서는
     * 두 트랜잭션이 모두 통과해 관리자 0명이 된다 (자동 리뷰 지적). 제거와 강등 두 곳에 흩어져 있던
     * 같은 검사를 여기로 모았다.
     */
    fun requireNotLastAdmin(teamId: UUID) {
        if (teamMembershipRepository.lockAllByTeamIdAndRole(teamId, TeamRole.ADMIN).size <= 1) {
            throw AdminRequiredException()
        }
    }
}
