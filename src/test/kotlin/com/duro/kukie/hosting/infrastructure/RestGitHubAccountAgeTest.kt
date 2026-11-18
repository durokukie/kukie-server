package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.OAuthProperties
import com.duro.kukie.hosting.application.port.out.GitHubAccountNow
import com.duro.kukie.hosting.exception.HostingUnavailableException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant

class RestGitHubAccountAgeTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val accountAge = RestGitHubAccountAge(
        builder.build(),
        OAuthProperties(
            github = OAuthProperties.Credential("github-client", "github-secret"),
            google = OAuthProperties.Credential("google-client", "google-secret"),
        ),
    )

    @Test
    fun `계정을 숫자 id 로 읽어 만든 때를 준다`() {
        // given
        server.expect(requestTo("https://api.github.com/user/583231"))
            // github-client:github-secret
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic Z2l0aHViLWNsaWVudDpnaXRodWItc2VjcmV0"))
            .andRespond(withSuccess("""{"id": 583231, "login": "octocat", "created_at": "2011-01-25T18:44:36Z"}""", MediaType.APPLICATION_JSON))

        // when & then
        accountAge.read(583231) shouldBe GitHubAccountNow("octocat", Instant.parse("2011-01-25T18:44:36Z"))
        server.verify()
    }

    @Test
    fun `GitHub 이 계정을 주지 않으면 통과시키지 않는다`() {
        listOf(
            withStatus(HttpStatus.NOT_FOUND),
            withStatus(HttpStatus.FORBIDDEN),
            withSuccess("""{"id": 583231, "login": "octocat"}""", MediaType.APPLICATION_JSON),
        ).forEach { response ->
            // given
            server.reset()
            server.expect(requestTo("https://api.github.com/user/583231")).andRespond(response)

            // when & then
            shouldThrow<HostingUnavailableException> { accountAge.read(583231) }
        }
    }
}
