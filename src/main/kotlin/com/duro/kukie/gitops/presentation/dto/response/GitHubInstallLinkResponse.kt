package com.duro.kukie.gitops.presentation.dto.response

import java.time.Instant

data class GitHubInstallLinkResponse(
    val installUrl: String,
    val expiresAt: Instant,
)
