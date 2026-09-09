package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamPermission
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeleteTeamService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamPermission: TeamPermission,
) {

    /**
     * 팀 삭제는 Admin만. 멤버십을 먼저 지우고 팀을 지운다.
     *
     * 클러스터·활동 기록의 보존 정책은 아직 정해지지 않았다(제품기획서 02 §5) — 지금은 팀에 딸린
     * 다른 데이터가 없어 이대로 지운다. 클러스터가 생기면 여기서 함께 다뤄야 한다.
     */
    @Transactional
    operator fun invoke(teamId: UUID, userId: UUID) {
        teamPermission.requireAdmin(teamId, userId)

        val team = teamRepository.findByIdOrThrow(teamId)
        teamMembershipRepository.deleteAllByTeamId(teamId)
        teamRepository.delete(team)
    }
}
