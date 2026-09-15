package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import com.duro.kukie.team.presentation.dto.request.UpdateTeamRequest
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UpdateTeamService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    /** 팀 정보 변경은 Admin만 (제품기획서 02). */
    @Transactional
    operator fun invoke(teamId: UUID, userId: UUID, request: UpdateTeamRequest): TeamResponse {
        val team = teamRepository.findByIdOrThrow(teamId)
        team.rename(request.name)

        // 역할 검사는 인터셉터가 했다. 응답에 넣을 역할만 다시 읽는다.
        val membership = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId, userId)

        return TeamResponse.of(team, membership.role)
    }
}
