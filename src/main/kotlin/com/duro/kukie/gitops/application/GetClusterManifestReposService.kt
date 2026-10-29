package com.duro.kukie.gitops.application

import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import com.duro.kukie.gitops.domain.ManifestRepoRepository
import com.duro.kukie.gitops.presentation.dto.response.ManifestRepoResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 에이전트용. 사용자 권한은 run 을 시작할 때 이미 보았다. */
@Service
class GetClusterManifestReposService(
    private val clusterRepository: ClusterRepository,
    private val manifestRepoRepository: ManifestRepoRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(clusterId: UUID): List<ManifestRepoResponse> {
        val cluster = clusterRepository.findByIdOrThrow(clusterId)
        return manifestRepoRepository.findAllByClusterIdOrderByCreatedAt(cluster.id).map(ManifestRepoResponse::from)
    }
}
