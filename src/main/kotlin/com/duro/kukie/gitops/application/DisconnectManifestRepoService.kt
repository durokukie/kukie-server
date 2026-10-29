package com.duro.kukie.gitops.application

import com.duro.kukie.cluster.application.ClusterPermission
import com.duro.kukie.gitops.application.port.out.GitHubAppGateway
import com.duro.kukie.gitops.domain.ManifestRepoRepository
import com.duro.kukie.gitops.exception.ManifestRepoNotConnectedException
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 레포 연결을 끊는다. 그 순간부터 토큰 발급을 거부한다. 다른 클러스터가 같은 레포를 쓰지 않으면 서버가 들고 있던 토큰도 거둔다.
 * GitHub 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다.
 */
@Service
class DisconnectManifestRepoService(
    private val clusterPermission: ClusterPermission,
    private val manifestRepoRepository: ManifestRepoRepository,
    private val gitHubAppGateway: GitHubAppGateway,
) {

    operator fun invoke(clusterId: UUID, repoId: UUID, userId: UUID) {
        val cluster = clusterPermission.requireWritable(clusterId, userId)
        val repo = manifestRepoRepository.findByIdAndClusterId(repoId, cluster.id) ?: throw ManifestRepoNotConnectedException()
        manifestRepoRepository.delete(repo)

        if (!manifestRepoRepository.existsByInstallationIdAndRepositoryId(repo.installationId, repo.repositoryId)) {
            gitHubAppGateway.revokeRepoToken(repo.installationId, repo.repositoryId)
        }
    }
}
