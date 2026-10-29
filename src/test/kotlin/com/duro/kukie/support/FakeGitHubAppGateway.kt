package com.duro.kukie.support

import com.duro.kukie.gitops.application.port.out.GitHubAppGateway
import com.duro.kukie.gitops.application.port.out.GitHubInstallationInfo
import com.duro.kukie.gitops.application.port.out.RepoToken
import com.duro.kukie.gitops.domain.GitHubAccount
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.gitops.exception.GitHubAppNotInstalledException
import java.time.Instant
import java.time.temporal.ChronoUnit

class FakeGitHubAppGateway : GitHubAppGateway, Resettable {

    /** 우리 앱의 설치. 여기 없는 id 는 다른 앱의 설치이거나 없는 설치다. */
    val installations = mutableMapOf<Long, GitHubInstallationInfo>()

    private val installationRepos = mutableMapOf<Long, List<RepoName>>()

    val issued = mutableListOf<Issued>()

    val revoked = mutableListOf<Issued>()

    /** 사용자 인증 code 마다 그 사용자가 접근할 수 있는 설치. */
    val userInstallations = mutableMapOf<String, Set<Long>>()

    /** GitHub 를 부른 횟수. 인증 없는 요청이 GitHub 를 부르지 않는지 본다. */
    var calls = 0
        private set

    fun install(
        installationId: Long,
        vararg repositories: String,
        account: String = "acme",
        suspended: Boolean = false,
        createdAt: Instant = Instant.now(),
    ) {
        installations[installationId] = GitHubInstallationInfo(
            installationId = installationId,
            account = GitHubAccount(login = account, type = "Organization"),
            repositorySelection = "selected",
            suspended = suspended,
            createdAt = createdAt.truncatedTo(ChronoUnit.SECONDS),
        )
        installationRepos[installationId] = repositories.map(RepoName::parse)
    }

    override fun findInstallation(installationId: Long): GitHubInstallationInfo? {
        calls++
        return installations[installationId]
    }

    override fun userInstallationIds(code: String): Set<Long> {
        calls++
        return userInstallations[code].orEmpty()
    }

    override fun installationRepositories(installationId: Long): List<RepoName> {
        calls++
        return installationRepos[installationId].orEmpty()
    }

    override fun findRepoInstallationId(repository: RepoName): Long? {
        calls++
        return installationRepos.entries.firstOrNull { repository in it.value }?.key
    }

    override fun issueRepoToken(installationId: Long, repository: RepoName): RepoToken {
        if (repository !in installationRepos[installationId].orEmpty()) throw GitHubAppNotInstalledException()
        return token(installationId, repository)
    }

    override fun issueRepoToken(installationId: Long, repositoryId: Long): RepoToken {
        val repository = installationRepos[installationId].orEmpty().firstOrNull { repositoryIdOf(it) == repositoryId }
            ?: throw GitHubAppNotInstalledException()
        return token(installationId, repository)
    }

    override fun revokeRepoToken(installationId: Long, repositoryId: Long) {
        revoked += Issued(installationId, repositoryId)
    }

    private fun token(installationId: Long, repository: RepoName): RepoToken {
        calls++
        val repositoryId = repositoryIdOf(repository)
        issued += Issued(installationId, repositoryId)
        return RepoToken(
            token = tokenFor(repository),
            expiresAt = EXPIRES_AT,
            repositoryId = repositoryId,
            repository = repository,
        )
    }

    override fun clear() {
        installations.clear()
        installationRepos.clear()
        issued.clear()
        revoked.clear()
        userInstallations.clear()
        calls = 0
    }

    data class Issued(
        val installationId: Long,
        val repositoryId: Long,
    )

    companion object {
        val EXPIRES_AT: Instant = Instant.parse("2026-10-30T10:00:00Z").truncatedTo(ChronoUnit.SECONDS)

        fun repositoryIdOf(repository: RepoName): Long = 1_000L + (repository.fullName.hashCode().toLong() and 0xffff)

        fun repositoryIdOf(repository: String): Long = repositoryIdOf(RepoName.parse(repository))

        fun tokenFor(repository: RepoName): String = "ghs_fake_${repository.owner}_${repository.name}"
    }
}
