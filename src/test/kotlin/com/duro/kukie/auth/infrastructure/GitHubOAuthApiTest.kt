package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.exception.OAuthLogInFailedException
import com.duro.kukie.global.config.properties.OAuthProperties
import com.duro.kukie.user.domain.GitHubAccount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class GitHubOAuthApiTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val api = GitHubOAuthApi(
        builder.build(),
        OAuthProperties(
            github = OAuthProperties.Credential("github-client", "github-secret"),
            google = OAuthProperties.Credential("google-client", "google-secret"),
        ),
    )

    @Test
    fun `프로필에 GitHub 숫자 id 와 계정명을 함께 싣는다`() {
        // given
        server.expect(requestTo("https://github.com/login/oauth/access_token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"access_token": "gho_token"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.github.com/user"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gho_token"))
            .andRespond(
                withSuccess(
                    """{"id": 583231, "login": "octocat", "name": "옥토캣", "avatar_url": "https://avatars.githubusercontent.com/u/583231"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server.expect(requestTo("https://api.github.com/user/emails"))
            .andRespond(
                withSuccess(
                    """[{"email": "other@example.com", "primary": false, "verified": true},
                        {"email": "me@example.com", "primary": true, "verified": true}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        // when
        val profile = api.fetchProfile("code", "http://127.0.0.1:3000/callback", null)

        // then
        profile.gitHub shouldBe GitHubAccount(id = 583231, login = "octocat")
        profile.email shouldBe "me@example.com"
        profile.name shouldBe "옥토캣"
        server.verify()
    }

    @Test
    fun `GitHub 가 숫자 id 를 주지 않으면 로그인에 실패한다`() {
        // given
        server.expect(requestTo("https://github.com/login/oauth/access_token"))
            .andRespond(withSuccess("""{"access_token": "gho_token"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.github.com/user"))
            .andRespond(withSuccess("""{"login": "octocat", "name": null, "avatar_url": null}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.github.com/user/emails"))
            .andRespond(withSuccess("""[{"email": "me@example.com", "primary": true, "verified": true}]""", MediaType.APPLICATION_JSON))

        // when & then
        shouldThrow<OAuthLogInFailedException> { api.fetchProfile("code", "http://127.0.0.1:3000/callback", null) }
    }

    @Test
    fun `인가 코드가 유효하지 않으면 로그인에 실패한다`() {
        // given: GitHub 은 유효하지 않은 코드에도 200 과 error 로 답한다
        server.expect(requestTo("https://github.com/login/oauth/access_token"))
            .andRespond(withSuccess("""{"error": "bad_verification_code"}""", MediaType.APPLICATION_JSON))

        // when & then
        shouldThrow<OAuthLogInFailedException> { api.fetchProfile("code", "http://127.0.0.1:3000/callback", null) }
    }
}
