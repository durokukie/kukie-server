package com.duro.kukie.gitops.application

import com.duro.kukie.gitops.application.port.out.GitHubAppGateway
import com.duro.kukie.gitops.domain.GitHubInstallRequestRepository
import com.duro.kukie.gitops.domain.GitHubInstallationRepository
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.gitops.presentation.dto.response.GitHubAppStatusResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

/** 팀에 묶인 설치를 GitHub 에서 다시 읽는다. GitHub 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
@Service
class RefreshGitHubAppService(
    private val gitHubInstallationRepository: GitHubInstallationRepository,
    private val gitHubInstallRequestRepository: GitHubInstallRequestRepository,
    private val gitHubAppGateway: GitHubAppGateway,
) {

    operator fun invoke(teamId: UUID): GitHubAppStatusResponse {
        val installations = gitHubInstallationRepository.findAllByTeamIdOrderByCreatedAt(teamId).map { installation ->
            val info = gitHubAppGateway.findInstallation(installation.installationId) ?: return@map installation
            val repositories = if (info.suspended) {
                emptyList()
            } else {
                gitHubAppGateway.installationRepositories(info.installationId).map(RepoName::fullName)
            }
            installation.refresh(info.account, info.repositorySelection, repositories, info.suspended)
            gitHubInstallationRepository.save(installation)
        }

        return GitHubAppStatusResponse.of(installations, gitHubInstallRequestRepository.findByIdOrNull(teamId))
    }
}
