package com.duro.kukie.gitops.application.port.`in`

import com.duro.kukie.gitops.domain.RepoName
import java.util.UUID

data class IssueRepoTokenCommand(
    val clusterId: UUID,
    val repository: RepoName,
)
