package com.duro.kukie.gitops.application

import com.duro.kukie.cluster.application.ClusterPermission
import com.duro.kukie.gitops.domain.ManifestRepoRepository
import com.duro.kukie.gitops.presentation.dto.response.ManifestRepoResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetManifestReposService(
    private val clusterPermission: ClusterPermission,
    private val manifestRepoRepository: ManifestRepoRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(clusterId: UUID, userId: UUID): List<ManifestRepoResponse> {
        val cluster = clusterPermission.requireReadable(clusterId, userId)
        return manifestRepoRepository.findAllByClusterIdOrderByCreatedAt(cluster.id).map(ManifestRepoResponse::from)
    }
}
