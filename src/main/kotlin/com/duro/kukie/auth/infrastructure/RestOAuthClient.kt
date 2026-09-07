package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.OAuthProvider
import org.springframework.stereotype.Component

@Component
class RestOAuthClient(
    private val gitHubOAuthApi: GitHubOAuthApi,
    private val googleOAuthApi: GoogleOAuthApi,
) : OAuthClient {

    override fun fetchProfile(
        provider: OAuthProvider,
        code: String,
        redirectUri: String,
        codeVerifier: String?,
    ): OAuthProfile = when (provider) {
        OAuthProvider.GITHUB -> gitHubOAuthApi.fetchProfile(code, redirectUri, codeVerifier)
        OAuthProvider.GOOGLE -> googleOAuthApi.fetchProfile(code, redirectUri, codeVerifier)
    }
}
