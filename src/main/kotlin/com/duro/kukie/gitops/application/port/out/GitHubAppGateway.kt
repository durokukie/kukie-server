package com.duro.kukie.gitops.application.port.out

import com.duro.kukie.gitops.domain.GitHubAccount
import com.duro.kukie.gitops.domain.RepoName
import java.time.Instant

/** Kukie GitHub App 으로 GitHub 를 부른다. 개인키와 토큰은 이 뒤에만 있다. */
interface GitHubAppGateway {

    /** 우리 앱의 설치가 아니거나 없으면 null. */
    fun findInstallation(installationId: Long): GitHubInstallationInfo?

    /** 설치가 볼 수 있는 레포들. */
    fun installationRepositories(installationId: Long): List<RepoName>

    /** 레포에 우리 앱 설치가 없으면 null. */
    fun findRepoInstallationId(repository: RepoName): Long?

    /** 그 레포 하나로 범위를 좁힌 설치 토큰. 설치가 레포를 볼 수 없으면 GitHubAppNotInstalledException. */
    fun issueRepoToken(installationId: Long, repository: RepoName): RepoToken

    fun issueRepoToken(installationId: Long, repositoryId: Long): RepoToken

    /** 연결을 끊은 레포의 토큰을 캐시에서 지우고 GitHub 에서도 거둔다(실패해도 던지지 않는다). */
    fun revokeRepoToken(installationId: Long, repositoryId: Long)
}

data class GitHubInstallationInfo(
    val installationId: Long,
    val account: GitHubAccount,
    val repositorySelection: String,
    val suspended: Boolean,
)

class RepoToken(
    val token: String,
    val expiresAt: Instant,
    val repositoryId: Long,
    val repository: RepoName,
) {
    override fun toString(): String = "RepoToken(repository=$repository, expiresAt=$expiresAt)"
}
