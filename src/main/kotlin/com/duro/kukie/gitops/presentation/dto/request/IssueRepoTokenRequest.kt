package com.duro.kukie.gitops.presentation.dto.request

import com.duro.kukie.gitops.application.port.`in`.IssueRepoTokenCommand
import com.duro.kukie.gitops.domain.RepoName
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class IssueRepoTokenRequest(
    /** `owner/repo` 또는 ArgoCD repoURL 그대로. */
    @field:NotBlank
    val repository: String,
) {
    fun toCommand(clusterId: UUID) = IssueRepoTokenCommand(
        clusterId = clusterId,
        repository = RepoName.parse(repository),
    )
}
