package com.duro.kukie.gitops.application.port.`in`

import com.duro.kukie.gitops.domain.RepoName
import java.util.UUID

data class ConnectManifestRepoCommand(
    val clusterId: UUID,
    val userId: UUID,
    val repository: RepoName,
)
