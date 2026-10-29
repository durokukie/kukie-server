package com.duro.kukie.gitops.presentation.dto.response

import com.duro.kukie.gitops.domain.ManifestRepo
import java.time.LocalDateTime
import java.util.UUID

data class ManifestRepoResponse(
    val id: UUID,
    val clusterId: UUID,
    /** 정규화한 소문자 `owner/repo`. */
    val repository: String,
    val repositoryId: Long,
    val installationId: Long,
    val connectedBy: UUID,
    val connectedAt: LocalDateTime,
) {
    companion object {
        fun from(repo: ManifestRepo) = ManifestRepoResponse(
            id = repo.id,
            clusterId = repo.clusterId,
            repository = repo.fullName,
            repositoryId = repo.repositoryId,
            installationId = repo.installationId,
            connectedBy = repo.connectedBy,
            connectedAt = repo.createdAt,
        )
    }
}
