package com.duro.kukie.team.application

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
) {

    /** 팀 삭제는 Admin만. agent 쪽 클러스터 정리는 후속 과제다. */
    @Transactional
    operator fun invoke(teamId: UUID) {
        teamInvitationRepository.deleteAllByTeamId(teamId)
        teamMembershipRepository.deleteAllByTeamId(teamId)
        teamRepository.deleteById(teamId)
    }
}
