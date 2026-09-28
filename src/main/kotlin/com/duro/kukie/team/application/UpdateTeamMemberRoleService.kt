package com.duro.kukie.team.application

import com.duro.kukie.notification.domain.Notification
import com.duro.kukie.notification.domain.NotificationRepository
import com.duro.kukie.team.application.port.`in`.UpdateTeamMemberRoleCommand
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.TeamRole
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

    /** 역할 변경은 Admin만. 승인 대신 알림을 남기고, Admin 이 0명이 되는 변경은 막는다. */
    @Transactional
    operator fun invoke(command: UpdateTeamMemberRoleCommand) {
        val target = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(command.teamId, command.targetUserId)
        if (target.role == command.role) {
            return
        }
        if (target.role == TeamRole.ADMIN && command.role != TeamRole.ADMIN) {
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
