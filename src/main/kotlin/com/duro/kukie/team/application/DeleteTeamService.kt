package com.duro.kukie.team.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeleteTeamService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamInvitationRepository: TeamInvitationRepository,
    private val clusterRepository: ClusterRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRunRepository: ChatRunRepository,
    private val actionPlanRepository: ActionPlanRepository,
) {

    /** 팀 삭제는 Admin만. */
    @Transactional
    operator fun invoke(teamId: UUID) {
        // 교착과 FK 위반을 막으려고 다른 곳의 잠금 순서(방 → run → 계획)에 맞춰 팀부터 차례로 잠근 뒤 지운다
        teamRepository.lockIdById(teamId)
        clusterRepository.lockIdsByTeamId(teamId)
        chatSessionRepository.lockIdsByTeamId(teamId)
        chatRunRepository.lockIdsByTeamId(teamId)
        actionPlanRepository.deleteAllByTeamId(teamId)
        chatRunRepository.deleteAllByTeamId(teamId)
        chatSessionRepository.deleteAllByTeamId(teamId)
        clusterRepository.deleteAllByTeamId(teamId)
        teamInvitationRepository.deleteAllByTeamId(teamId)
        teamMembershipRepository.deleteAllByTeamId(teamId)
        teamRepository.deleteById(teamId)
    }
}
