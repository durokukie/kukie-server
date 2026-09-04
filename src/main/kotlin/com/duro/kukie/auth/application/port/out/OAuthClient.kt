package com.duro.kukie.auth.application.port.out

import com.duro.kukie.auth.domain.OAuthProvider

interface OAuthClient {
    fun fetchProfile(provider: OAuthProvider, code: String, redirectUri: String, codeVerifier: String?): OAuthProfile
}
