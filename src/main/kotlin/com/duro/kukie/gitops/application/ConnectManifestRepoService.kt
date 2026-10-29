package com.duro.kukie.gitops.application

import com.duro.kukie.cluster.application.ClusterPermission
import com.duro.kukie.gitops.application.port.`in`.ConnectManifestRepoCommand
import com.duro.kukie.gitops.application.port.out.GitHubAppGateway
import com.duro.kukie.gitops.domain.GitHubInstallationRepository
import com.duro.kukie.gitops.domain.ManifestRepo
import com.duro.kukie.gitops.domain.ManifestRepoRepository
import com.duro.kukie.gitops.exception.GitHubAppNotInstalledException
import com.duro.kukie.gitops.presentation.dto.response.ManifestRepoResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * 클러스터에 매니페스트 레포를 잇는다. 이 팀에 묶인 우리 앱 설치가 그 레포를 볼 수 있어야 한다.
 * GitHub 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다(저장은 한 번).
 */
@Service
class ConnectManifestRepoService(
    private val clusterPermission: ClusterPermission,
    private val manifestRepoRepository: ManifestRepoRepository,
    private val gitHubInstallationRepository: GitHubInstallationRepository,
    private val gitHubAppGateway: GitHubAppGateway,
) {

    operator fun invoke(command: ConnectManifestRepoCommand): ConnectManifestRepoResult {
        val cluster = clusterPermission.requireWritable(command.clusterId, command.userId)
        manifestRepoRepository.findByClusterIdAndFullName(cluster.id, command.repository.fullName)
            ?.let { return ConnectManifestRepoResult(ManifestRepoResponse.from(it), created = false) }

        val installationId = gitHubAppGateway.findRepoInstallationId(command.repository)
            ?: throw GitHubAppNotInstalledException()
        val installation = gitHubInstallationRepository.findByInstallationId(installationId)
        if (installation?.teamId != cluster.teamId) throw GitHubAppNotInstalledException()

        // 레포 하나로 좁힌 토큰을 받아 보면 설치가 그 레포를 볼 수 있는지와 레포 id 를 한 번에 안다
        val token = gitHubAppGateway.issueRepoToken(installationId, command.repository)
        val repo = ManifestRepo(
            teamId = cluster.teamId,
            clusterId = cluster.id,
            installationId = installationId,
            repositoryId = token.repositoryId,
            repository = token.repository,
            connectedBy = command.userId,
        )
        return try {
            ConnectManifestRepoResult(ManifestRepoResponse.from(manifestRepoRepository.saveAndFlush(repo)), created = true)
        } catch (exception: DataIntegrityViolationException) {
            val existing = manifestRepoRepository.findByClusterIdAndRepositoryId(cluster.id, token.repositoryId)
                ?: throw exception
            ConnectManifestRepoResult(ManifestRepoResponse.from(existing), created = false)
        }
    }
}
