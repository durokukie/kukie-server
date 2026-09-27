package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.OAuthLogInFailedException
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class GoogleOAuthApi(
    private val restClient: RestClient,
    private val credentialsResolver: OAuthCredentialsResolver,
) {

    fun fetchProfile(code: String, redirectUri: String, codeVerifier: String?): OAuthProfile {
        val accessToken = exchangeCode(code, redirectUri, codeVerifier)
        val userInfo = fetchUserInfo(accessToken)

        val email = userInfo.email?.takeIf { userInfo.emailVerified == true }
            ?: throw OAuthLogInFailedException()

        return OAuthProfile(email = email, name = userInfo.name ?: email.substringBefore("@"))
    }

    private fun exchangeCode(code: String, redirectUri: String, codeVerifier: String?): String {
        val credentials = credentialsResolver.resolve(OAuthProvider.GOOGLE, redirectUri)
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("code", code)
            add("client_id", credentials.clientId)
            add("client_secret", credentials.clientSecret)
            add("redirect_uri", redirectUri)
            codeVerifier?.let { add("code_verifier", it) }
        }

        val response = restClient.post()
            .uri(TOKEN_URL)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError) { _, _ -> throw OAuthLogInFailedException() }
            .body<TokenExchangeResponse>()

        return response?.accessToken ?: throw OAuthLogInFailedException()
    }

    private fun fetchUserInfo(accessToken: String): UserInfoResponse =
        restClient.get()
            .uri(USER_INFO_URL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body<UserInfoResponse>()
            ?: throw OAuthLogInFailedException()

    private data class TokenExchangeResponse(
        @JsonProperty("access_token")
        val accessToken: String?,
    )

    private data class UserInfoResponse(
        val email: String?,
        @JsonProperty("email_verified")
        val emailVerified: Boolean?,
        val name: String?,
    )

    companion object {
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
    }
}
