package com.duro.kukie.gitops.presentation.dto.request

import com.duro.kukie.gitops.application.port.`in`.ConnectManifestRepoCommand
import com.duro.kukie.gitops.domain.RepoName
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class ConnectManifestRepoRequest(
    @field:NotBlank
    val url: String,
) {
    fun toCommand(clusterId: UUID, userId: UUID) = ConnectManifestRepoCommand(
        clusterId = clusterId,
        userId = userId,
        repository = RepoName.parse(url),
    )
}
