package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamPermission
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.domain.findMembershipOrThrow
import com.duro.kukie.team.exception.AdminRequiredException
import com.duro.kukie.team.presentation.dto.request.UpdateTeamMemberRoleRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UpdateTeamMemberRoleService(
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamPermission: TeamPermission,
) {

    /**
     * 역할 변경은 Admin만. 상대방 승인은 받지 않는다 (제품기획서 02).
     * 결과적으로 Admin이 0명이 되는 변경은 막는다 — 팀에 관리자가 없으면 아무도 팀을 관리할 수 없다.
     */
    @Transactional
    operator fun invoke(teamId: UUID, targetUserId: UUID, userId: UUID, request: UpdateTeamMemberRoleRequest) {
        teamPermission.requireAdmin(teamId, userId)

        val target = teamMembershipRepository.findMembershipOrThrow(teamId, targetUserId)
        if (target.role == request.role) {
            return
        }
        if (demotesLastAdmin(teamId, target.role, request.role)) {
            throw AdminRequiredException()
        }

        target.changeRole(request.role)
    }

    private fun demotesLastAdmin(teamId: UUID, current: TeamRole, next: TeamRole): Boolean =
        current.isAdmin && next.isAdmin.not() && teamMembershipRepository.countByTeamIdAndRole(teamId, TeamRole.ADMIN) <= 1
}
