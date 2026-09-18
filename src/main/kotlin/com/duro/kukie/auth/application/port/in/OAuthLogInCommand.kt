package com.duro.kukie.auth.application.port.`in`

import com.duro.kukie.auth.domain.OAuthProvider

data class OAuthLogInCommand(
    val provider: OAuthProvider,
    val code: String,
    val redirectUri: String,
    val codeVerifier: String?,
)
