package com.duro.kukie.gitops.application

import com.duro.kukie.gitops.presentation.dto.response.ManifestRepoResponse

data class ConnectManifestRepoResult(
    val repo: ManifestRepoResponse,
    val created: Boolean,
)
