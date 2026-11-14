package com.duro.kukie.user.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.notification.domain.NotificationRepository
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.team.application.DeleteTeamService
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.lockByIdOrThrow
import com.duro.kukie.user.exception.LastAdminOfTeamException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 회원 탈퇴. 다른 팀원이 있는 팀의 유일한 관리자면 거절한다. 혼자인 팀은 같이 지우고, 개인 대화·알림·서빙 접근을 정리한 뒤
 * 사용자 행을 익명화한다. 팀 공유 대화와 등록한 클러스터는 팀 자료라 남긴다.
 */
@Service
class DeleteAccountService(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val deleteTeamService: DeleteTeamService,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRunRepository: ChatRunRepository,
    private val actionPlanRepository: ActionPlanRepository,
    private val notificationRepository: NotificationRepository,
    private val servingAccessRepository: ServingAccessRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Transactional
    operator fun invoke(userId: UUID) {
        val user = userRepository.lockByIdOrThrow(userId)

        leaveTeams(userId)
        deletePrivateChats(userId)
        notificationRepository.deleteAllByUserId(userId)
        servingAccessRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, ServingAccessStatus.ACTIVE)
            .forEach { it.revoke() }

        user.anonymize(utcNow())
        refreshTokenRepository.deleteAll(userId)
    }

    // 팀 삭제·관리자 변경과 같은 순서로 팀부터 잠근 뒤 구성원을 읽는다
    private fun leaveTeams(userId: UUID) {
        val memberships = teamMembershipRepository.findAllByUserId(userId).sortedBy { it.teamId }
        memberships.forEach { teamRepository.lockIdById(it.teamId) }

        val (alone, shared) = memberships.partition { teamMembershipRepository.findAllByTeamId(it.teamId).size == 1 }
        shared.filter { it.role == TeamRole.ADMIN }.forEach {
            if (teamMembershipRepository.lockAllByTeamIdAndRole(it.teamId, TeamRole.ADMIN).size <= 1) throw LastAdminOfTeamException()
        }

        alone.forEach { deleteTeamService(it.teamId) }
        teamMembershipRepository.deleteAll(shared)
    }

    private fun deletePrivateChats(userId: UUID) {
        chatSessionRepository.lockPrivateIdsByUserId(userId).forEach { sessionId ->
            actionPlanRepository.deleteAllBySessionId(sessionId)
            chatRunRepository.deleteAllBySessionId(sessionId)
            chatSessionRepository.deleteById(sessionId)
        }
    }
}
