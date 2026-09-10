package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.presentation.dto.request.UpdateTeamRequest
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UpdateTeamService(
    private val teamRepository: TeamRepository,
    private val teamPermission: TeamPermission,
) {

    /** 팀 정보 변경은 Admin만 (제품기획서 02). */
    @Transactional
    operator fun invoke(teamId: UUID, userId: UUID, request: UpdateTeamRequest): TeamResponse {
        val membership = teamPermission.requireAdmin(teamId, userId)
        val team = teamRepository.findByIdOrThrow(teamId)
        team.rename(request.name)

        return TeamResponse.of(team, membership.role)
    }
}
