package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.exception.NotTeamMemberException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetClustersService(
    private val clusterRepository: ClusterRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID, teamId: UUID?): List<ClusterResponse> {
        val clusters = if (teamId == null) {
            clusterRepository.findAllVisibleTo(userId)
        } else {
            if (!teamMembershipRepository.existsByTeamIdAndUserId(teamId, userId)) throw NotTeamMemberException()
            clusterRepository.findAllByTeamIdOrderByCreatedAt(teamId)
        }

        return clusters.map(ClusterResponse::from)
    }
}
