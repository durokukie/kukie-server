package com.duro.kukie.gitops

import com.duro.kukie.gitops.domain.GitHubAccount
import com.duro.kukie.gitops.domain.GitHubInstallation
import com.duro.kukie.gitops.domain.ManifestRepo
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.support.FakeGitHubAppGateway
import java.util.UUID

object GitOpsFixture {

    const val INSTALLATION_ID = 7L
    const val REPOSITORY = "acme/manifests"

    fun installation(
        teamId: UUID,
        installedBy: UUID,
        installationId: Long = INSTALLATION_ID,
        repositories: List<String> = listOf(REPOSITORY),
    ): GitHubInstallation = GitHubInstallation(
        teamId = teamId,
        installationId = installationId,
        installedBy = installedBy,
        account = GitHubAccount(login = "acme", type = "Organization"),
        repositorySelection = "selected",
        repositories = repositories,
        suspended = false,
    )

    fun manifestRepo(
        teamId: UUID,
        clusterId: UUID,
        connectedBy: UUID,
        repository: String = REPOSITORY,
        installationId: Long = INSTALLATION_ID,
    ): ManifestRepo = ManifestRepo(
        teamId = teamId,
        clusterId = clusterId,
        installationId = installationId,
        repositoryId = FakeGitHubAppGateway.repositoryIdOf(repository),
        repository = RepoName.parse(repository),
        connectedBy = connectedBy,
    )
}
