package com.duro.kukie.team.application

import com.duro.kukie.notification.domain.Notification
import com.duro.kukie.notification.domain.NotificationRepository
import com.duro.kukie.team.application.port.`in`.UpdateTeamMemberRoleCommand
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateTeamMemberRoleService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val notificationRepository: NotificationRepository,
    private val teamPermission: TeamPermission,
) {

    /**
     * 역할 변경은 Admin만. 상대방 승인은 받지 않고 대신 알림을 남긴다 (제품기획서 02 §3) —
     * 승인 절차가 없으므로 알림이 당사자가 아는 유일한 방법이다.
     *
     * 결과적으로 Admin이 0명이 되는 변경은 막는다 — 팀에 관리자가 없으면 아무도 팀을 관리할 수 없다.
     */
    @Transactional
    operator fun invoke(command: UpdateTeamMemberRoleCommand) {
        val target = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(command.teamId, command.targetUserId)
        if (target.role == command.role) {
            return
        }
        if (target.role.isAdmin && command.role.isAdmin.not()) {
            teamPermission.requireNotLastAdmin(command.teamId)
        }

        target.changeRole(command.role)

        val team = teamRepository.findByIdOrThrow(command.teamId)
        notificationRepository.save(
            Notification.roleChanged(
                userId = command.targetUserId,
                teamId = team.id,
                teamName = team.name,
                role = command.role,
            ),
        )
    }
}
