package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.AdminRequiredException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * "이 사용자가 이 팀에서 무엇을 할 수 있나" 를 판단하는 한 곳.
 *
 * use case 서비스가 아니라 그 앞에 서는 공통 검사라 `operator fun invoke` 규칙을 따르지 않는다.
 * `domain/` 에 두었다가 옮겼다 — CLAUDE.md 가 그 폴더를 엔티티와 리포지토리 추상으로 한정한다
 * (자동 리뷰 둘이 각각 지적).
 */
@Component
class TeamPermission(
    private val teamMembershipRepository: TeamMembershipRepository,
) {

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
