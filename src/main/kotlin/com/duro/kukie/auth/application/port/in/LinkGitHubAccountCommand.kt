package com.duro.kukie.auth.application.port.`in`

import java.util.UUID

data class LinkGitHubAccountCommand(
    val userId: UUID,
    val code: String,
    val redirectUri: String,
    val codeVerifier: String,
)
