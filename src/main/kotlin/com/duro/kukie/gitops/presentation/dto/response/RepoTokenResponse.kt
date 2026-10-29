package com.duro.kukie.gitops.presentation.dto.response

import com.duro.kukie.gitops.application.port.out.RepoToken
import java.time.Instant

class RepoTokenResponse(
    val repository: String,
    val repositoryId: Long,
    val token: String,
    val expiresAt: Instant,
) {
    override fun toString(): String = "RepoTokenResponse(repository=$repository, expiresAt=$expiresAt)"

    companion object {
        fun from(token: RepoToken) = RepoTokenResponse(
            repository = token.repository.fullName,
            repositoryId = token.repositoryId,
            token = token.token,
            expiresAt = token.expiresAt,
        )
    }
}
