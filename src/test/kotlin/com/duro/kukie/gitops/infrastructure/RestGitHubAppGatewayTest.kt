package com.duro.kukie.gitops.infrastructure

import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.gitops.exception.GitHubAppNotInstalledException
import com.duro.kukie.gitops.exception.GitHubAppSuspendedException
import com.duro.kukie.global.config.properties.GitHubAppProperties
import io.jsonwebtoken.Jwts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestGitHubAppGatewayTest {

    private val now = Instant.parse("2026-10-30T09:00:00Z")
    private val properties = GitHubAppProperties(
        id = APP_ID,
        slug = "kukie-gitops",
        privateKey = TestRsaKeys.pkcs1Pem(),
        apiUrl = API,
        webUrl = "https://github.com",
    )
    private val credentials = GitHubAppCredentials(properties, Clock.fixed(now, ZoneOffset.UTC))
    private val builder = RestClient.builder().baseUrl(API)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val gateway = RestGitHubAppGateway(builder.build(), properties, credentials)

    @Test
    fun `레포 id 로 범위를 좁힌 설치 토큰을 App JWT 로 받고 남은 시간 동안 다시 쓴다`() {
        // given
        server.expect(ExpectedCount.once(), requestTo("$API/app/installations/7/access_tokens"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
            .andExpect { request -> verifiedAppJwt(request.headers.getFirst(HttpHeaders.AUTHORIZATION)!!.removePrefix("Bearer ")) }
            .andExpect(content().json("""{"repository_ids":[42]}""", org.springframework.test.json.JsonCompareMode.STRICT))
            .andRespond(withSuccess(tokenBody("ghs_scoped", 42, "Acme/Manifests"), MediaType.APPLICATION_JSON))

        // when
        val first = gateway.issueRepoToken(7, 42)
        val again = gateway.issueRepoToken(7, 42)

        // then
        server.verify()
        first.token shouldBe "ghs_scoped"
        first.repositoryId shouldBe 42
        first.repository shouldBe RepoName("acme", "manifests")
        first.expiresAt shouldBe Instant.parse("2026-10-30T10:00:00Z")
        again.token shouldBe "ghs_scoped"
        first.toString().contains("ghs_scoped") shouldBe false
    }

    @Test
    fun `레포 이름으로 받을 때는 owner 를 뺀 이름만 보낸다`() {
        // given
        server.expect(requestTo("$API/app/installations/7/access_tokens"))
            .andExpect(content().json("""{"repositories":["manifests"]}""", org.springframework.test.json.JsonCompareMode.STRICT))
            .andRespond(withSuccess(tokenBody("ghs_named", 42, "acme/manifests"), MediaType.APPLICATION_JSON))

        // when
        val token = gateway.issueRepoToken(7, RepoName("acme", "manifests"))

        // then
        server.verify()
        token.repositoryId shouldBe 42
    }

    @Test
    fun `설치가 레포를 볼 수 없으면 설치 안 됨으로 거부한다`() {
        // given
        server.expect(requestTo("$API/app/installations/7/access_tokens"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT))

        // when & then
        shouldThrow<GitHubAppNotInstalledException> { gateway.issueRepoToken(7, 42) }
    }

    @Test
    fun `정지된 설치면 정지됨으로 거부한다`() {
        // given
        server.expect(requestTo("$API/app/installations/7/access_tokens"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        // when & then
        shouldThrow<GitHubAppSuspendedException> { gateway.issueRepoToken(7, 42) }
    }

    @Test
    fun `우리 앱의 설치면 계정과 레포 고르기 방식을 읽는다`() {
        // given
        server.expect(requestTo("$API/app/installations/7"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(installationBody(APP_ID), MediaType.APPLICATION_JSON))

        // when
        val installation = gateway.findInstallation(7)!!

        // then
        installation.account.login shouldBe "acme"
        installation.account.type shouldBe "Organization"
        installation.repositorySelection shouldBe "selected"
        installation.suspended shouldBe false
    }

    @Test
    fun `다른 앱의 설치이거나 없는 설치는 없는 것으로 본다`() {
        // given
        server.expect(requestTo("$API/app/installations/7"))
            .andRespond(withSuccess(installationBody(appId = 1), MediaType.APPLICATION_JSON))
        server.expect(requestTo("$API/app/installations/8"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        // when
        val other = gateway.findInstallation(7)
        val missing = gateway.findInstallation(8)

        // then
        other.shouldBeNull()
        missing.shouldBeNull()
    }

    @Test
    fun `연결을 끊으면 들고 있던 토큰을 그 토큰으로 거둔다`() {
        // given
        server.expect(requestTo("$API/app/installations/7/access_tokens"))
            .andRespond(withSuccess(tokenBody("ghs_scoped", 42, "acme/manifests"), MediaType.APPLICATION_JSON))
        server.expect(requestTo("$API/installation/token"))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghs_scoped"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))
        gateway.issueRepoToken(7, 42)

        // when
        gateway.revokeRepoToken(7, 42)

        // then
        server.verify()
        credentials.cachedRepoToken(7, 42).shouldBeNull()
    }

    private fun verifiedAppJwt(jwt: String) {
        Jwts.parser()
            .verifyWith(TestRsaKeys.keyPair.public as RSAPublicKey)
            .clock { java.util.Date.from(now) }
            .build()
            .parseSignedClaims(jwt)
            .payload.issuer shouldBe APP_ID.toString()
    }

    private fun tokenBody(token: String, repositoryId: Long, fullName: String) = """
        {
          "token": "$token",
          "expires_at": "2026-10-30T10:00:00Z",
          "permissions": {"contents": "write", "pull_requests": "write", "metadata": "read"},
          "repository_selection": "selected",
          "repositories": [{"id": $repositoryId, "full_name": "$fullName", "name": "${fullName.substringAfter('/')}"}]
        }
    """.trimIndent()

    private fun installationBody(appId: Long) = """
        {
          "id": 7,
          "app_id": $appId,
          "account": {"login": "acme", "type": "Organization", "id": 99},
          "repository_selection": "selected",
          "suspended_at": null
        }
    """.trimIndent()

    companion object {
        private const val API = "https://api.github.test"
        private const val APP_ID = 5531779L
    }
}
