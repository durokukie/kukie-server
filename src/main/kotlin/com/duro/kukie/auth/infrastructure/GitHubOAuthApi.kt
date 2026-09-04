package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.exception.OAuthLogInFailedException
import com.duro.kukie.global.config.properties.OAuthProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class GitHubOAuthApi(
    private val restClient: RestClient,
    private val oAuthProperties: OAuthProperties,
) {

    fun fetchProfile(code: String, redirectUri: String, codeVerifier: String?): OAuthProfile {
        val accessToken = exchangeCode(code, redirectUri, codeVerifier)
        val user = fetchUser(accessToken)
        val email = fetchVerifiedEmail(accessToken)

        return OAuthProfile(email = email, name = user.name ?: user.login)
    }

    private fun exchangeCode(code: String, redirectUri: String, codeVerifier: String?): String {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("client_id", oAuthProperties.github.clientId)
            add("client_secret", oAuthProperties.github.clientSecret)
            add("code", code)
            add("redirect_uri", redirectUri)
            codeVerifier?.let { add("code_verifier", it) }
        }

        val response = restClient.post()
            .uri(TOKEN_URL)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError) { _, _ -> throw OAuthLogInFailedException() }
            .body<TokenExchangeResponse>()

        // GitHub은 유효하지 않은 인가 코드에도 200 상태 코드와 error 필드로 응답한다
        if (response?.accessToken == null || response.error != null) {
            throw OAuthLogInFailedException()
        }

        return response.accessToken
    }

    private fun fetchUser(accessToken: String): GitHubUserResponse =
        restClient.get()
            .uri(USER_URL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body<GitHubUserResponse>()
            ?: throw OAuthLogInFailedException()

    private fun fetchVerifiedEmail(accessToken: String): String {
        val emails = restClient.get()
            .uri(EMAILS_URL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body<List<GitHubEmailResponse>>()
            .orEmpty()

        val email = emails.firstOrNull { it.primary && it.verified } ?: emails.firstOrNull { it.verified }

        return email?.email ?: throw OAuthLogInFailedException()
    }

    private data class TokenExchangeResponse(
        @JsonProperty("access_token")
        val accessToken: String?,
        val error: String?,
    )

    private data class GitHubUserResponse(
        val login: String,
        val name: String?,
    )

    private data class GitHubEmailResponse(
        val email: String,
        val primary: Boolean,
        val verified: Boolean,
    )

    companion object {
        private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        private const val USER_URL = "https://api.github.com/user"
        private const val EMAILS_URL = "https://api.github.com/user/emails"
    }
}
