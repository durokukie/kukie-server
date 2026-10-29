package com.duro.kukie.gitops.presentation.dto.response

import com.duro.kukie.gitops.domain.GitHubInstallRequest
import com.duro.kukie.gitops.domain.GitHubInstallation
import java.time.LocalDateTime

data class GitHubAppStatusResponse(
    val status: GitHubAppStatus,
    /** 조직 관리자 승인을 기다리기 시작한 때. 기다리는 요청이 없으면 null. */
    val requestedAt: LocalDateTime?,
    val installations: List<Installation>,
) {
    enum class GitHubAppStatus { INSTALLED, PENDING, NONE }

    data class Installation(
        val installationId: Long,
        val account: String,
        val accountType: String,
        val repositorySelection: String,
        val repositories: List<String>,
        val suspended: Boolean,
        val installedAt: LocalDateTime,
    ) {
        companion object {
            fun from(installation: GitHubInstallation) = Installation(
                installationId = installation.installationId,
                account = installation.accountLogin,
                accountType = installation.accountType,
                repositorySelection = installation.repositorySelection,
                repositories = installation.repositories,
                suspended = installation.suspended,
                installedAt = installation.createdAt,
            )
        }
    }

    companion object {
        fun of(installations: List<GitHubInstallation>, request: GitHubInstallRequest?): GitHubAppStatusResponse {
            val status = when {
                installations.isNotEmpty() -> GitHubAppStatus.INSTALLED
                request != null -> GitHubAppStatus.PENDING
                else -> GitHubAppStatus.NONE
            }
            return GitHubAppStatusResponse(status, request?.requestedAt, installations.map(Installation::from))
        }
    }
}
