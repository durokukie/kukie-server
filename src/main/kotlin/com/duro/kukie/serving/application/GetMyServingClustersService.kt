package com.duro.kukie.serving.application

import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.serving.presentation.dto.response.MyServingClusterResponse
import com.duro.kukie.team.domain.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetMyServingClustersService(
    private val accesses: ServingAccessRepository, private val clusters: ClusterRepository, private val teams: TeamRepository,
) {
    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): List<MyServingClusterResponse> {
        val active = accesses.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, ServingAccessStatus.ACTIVE)
        val byId = clusters.findAllById(active.map { it.clusterId }).associateBy { it.id }
        val providers = teams.findAllById(byId.values.map { it.teamId }).associateBy { it.id }
        return active.mapNotNull { access ->
            val cluster = byId[access.clusterId] ?: return@mapNotNull null
            val provider = providers[cluster.teamId] ?: return@mapNotNull null
            MyServingClusterResponse(access.id, cluster.id, cluster.name, provider.name)
        }
    }
}
