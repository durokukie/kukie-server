package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamPermission
import com.duro.kukie.team.domain.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeleteTeamService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamInvitationRepository: TeamInvitationRepository,
    private val teamPermission: TeamPermission,
) {

    /**
     * 팀 삭제는 Admin만. 팀을 가리키는 것들(멤버십·초대)을 먼저 지우고 팀을 지운다.
     *
     * 알림은 팀을 참조하지 않고 팀 이름을 그때 값으로 적어 두므로 그대로 남는다.
     *
     * **클러스터는 여기서 지울 수 없다.** 클러스터 접속 정보는 agent DB(`tbl_cluster`)가 갖기로
     * 정해졌고(기획 04 §8, DB 구조 문서 합의 2026-09-10), 그쪽 `team_id` 는 이 서버를 향한 FK 가
     * 아니다. 팀을 지우면 agent 에 주인 없는 클러스터가 남는다 — agent 가 팀 목록을 이 서버에 물어
     * 판단하므로 아무도 쓸 수는 없지만, 지우려면 이 서버가 agent 에 알려 줘야 한다 (후속 과제).
     */
    @Transactional
    operator fun invoke(teamId: UUID, userId: UUID) {
        teamPermission.requireAdmin(teamId, userId)

        teamInvitationRepository.deleteAllByTeamId(teamId)
        teamMembershipRepository.deleteAllByTeamId(teamId)
        teamRepository.deleteById(teamId)
    }
}
