package com.duro.kukie.team.application

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
) {

    /** 팀 삭제는 Admin만. */
    @Transactional
    operator fun invoke(teamId: UUID) {
        // 지우는 사이에 팀 클러스터가 새로 생겨 FK 위반이 나지 않게 팀부터 잠근 뒤 지운다
        teamRepository.lockIdById(teamId)
        clusterRepository.lockIdsByTeamId(teamId)
        clusterRepository.deleteAllByTeamId(teamId)
        teamInvitationRepository.deleteAllByTeamId(teamId)
        teamMembershipRepository.deleteAllByTeamId(teamId)
        teamRepository.deleteById(teamId)
    }
}
