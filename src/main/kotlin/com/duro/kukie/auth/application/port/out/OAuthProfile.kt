package com.duro.kukie.auth.application.port.out

import com.duro.kukie.user.domain.GitHubAccount

data class OAuthProfile(
    val email: String,
    val name: String,
    val avatarUrl: String? = null,
    val gitHub: GitHubAccount? = null,
)
