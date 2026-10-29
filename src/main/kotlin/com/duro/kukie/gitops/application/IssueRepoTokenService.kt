package com.duro.kukie.gitops.application

import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import com.duro.kukie.gitops.application.port.`in`.IssueRepoTokenCommand
import com.duro.kukie.gitops.application.port.out.GitHubAppGateway
import com.duro.kukie.gitops.domain.ManifestRepoRepository
import com.duro.kukie.gitops.domain.findConnectedOrThrow
import com.duro.kukie.gitops.presentation.dto.response.RepoTokenResponse
import org.springframework.stereotype.Service

/** 에이전트에게 연결된 레포 하나로만 범위를 좁힌 1시간짜리 설치 토큰을 준다. 연결이 없으면 거부한다. */
@Service
class IssueRepoTokenService(
    private val clusterRepository: ClusterRepository,
    private val manifestRepoRepository: ManifestRepoRepository,
    private val gitHubAppGateway: GitHubAppGateway,
) {

    operator fun invoke(command: IssueRepoTokenCommand): RepoTokenResponse {
        val cluster = clusterRepository.findByIdOrThrow(command.clusterId)
        val repo = manifestRepoRepository.findConnectedOrThrow(cluster.id, command.repository)

        return RepoTokenResponse.from(gitHubAppGateway.issueRepoToken(repo.installationId, repo.repositoryId))
    }
}
