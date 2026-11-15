package com.duro.kukie.serving.application

import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.serving.presentation.dto.response.ServingInvitationResponse
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetMyServingInvitationsService(
    private val accesses: ServingAccessRepository, private val clusters: ClusterRepository,
    private val teams: TeamRepository, private val users: UserRepository,
) {
    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): List<ServingInvitationResponse> {
        val pending = accesses.findAllByEmailAndStatusAndInvitationExpiresAtAfterOrderByCreatedAtDesc(
            users.findByIdOrThrow(userId).email, ServingAccessStatus.PENDING, utcNow(),
        )
        val byId = clusters.findAllById(pending.map { it.clusterId }).associateBy { it.id }
        val providers = teams.findAllById(byId.values.map { it.teamId }).associateBy { it.id }
        return pending.mapNotNull { access ->
            val cluster = byId[access.clusterId] ?: return@mapNotNull null
            val provider = providers[cluster.teamId] ?: return@mapNotNull null
            ServingInvitationResponse(access.id, cluster.id, cluster.name, provider.name, access.invitationExpiresAt)
        }
    }
}
