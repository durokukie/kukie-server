package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import com.duro.kukie.team.exception.NotTeamAdminException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ClusterPermission(
    private val clusterRepository: ClusterRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    fun requireReadable(clusterId: UUID, userId: UUID): Cluster {
        val cluster = clusterRepository.findByIdOrThrow(clusterId)
        if (!teamMembershipRepository.existsByTeamIdAndUserId(cluster.teamId, userId)) throw ClusterNotFoundException()

        return cluster
    }

    fun requireWritable(clusterId: UUID, userId: UUID): Cluster {
        val cluster = requireReadable(clusterId, userId)
        requireTeamAdmin(cluster.teamId, userId)

        return cluster
    }

    fun requireTeamAdmin(teamId: UUID, userId: UUID) {
        val membership = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId, userId)
        if (membership.role != TeamRole.ADMIN) throw NotTeamAdminException()
    }
}
