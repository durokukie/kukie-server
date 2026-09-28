package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.AdminRequiredException
import org.springframework.stereotype.Component
import java.util.UUID

/** 팀 권한 검사를 모은 곳. use case 서비스가 아니라 `operator fun invoke` 규칙을 따르지 않는다. */
@Component
class TeamPermission(
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    /** ADMIN 을 줄이는 변경 전에 부른다. 동시 제거로 관리자가 0명이 되지 않게 잠그고 읽는다. */
    fun requireNotLastAdmin(teamId: UUID) {
        if (teamMembershipRepository.lockAllByTeamIdAndRole(teamId, TeamRole.ADMIN).size <= 1) {
            throw AdminRequiredException()
        }
    }
}
