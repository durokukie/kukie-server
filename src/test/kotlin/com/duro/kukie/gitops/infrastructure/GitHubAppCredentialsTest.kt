package com.duro.kukie.gitops.infrastructure

import com.duro.kukie.gitops.application.port.out.RepoToken
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.global.config.properties.GitHubAppProperties
import io.jsonwebtoken.Jwts
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class GitHubAppCredentialsTest {

    private val now = Instant.parse("2026-10-30T09:00:00Z")
    private val clock = MutableClock(now)

    @Test
    fun `GitHub 가 준 PKCS#1 키로 App ID 를 발급자로 한 10분짜리 RS256 JWT 를 서명한다`() {
        // given
        val credentials = GitHubAppCredentials(properties(TestRsaKeys.pkcs1Pem()), clock)

        // when
        val jwt = credentials.appJwt()

        // then
        val parsed = Jwts.parser()
            .verifyWith(TestRsaKeys.keyPair.public as RSAPublicKey)
            .clock { java.util.Date.from(clock.instant()) }
            .build()
            .parseSignedClaims(jwt)
        parsed.header.algorithm shouldBe "RS256"
        parsed.payload.issuer shouldBe "5531779"
        parsed.payload.issuedAt.toInstant() shouldBe now.minusSeconds(60)
        parsed.payload.expiration.toInstant() shouldBe now.minusSeconds(60).plus(Duration.ofMinutes(10))
    }

    @Test
    fun `줄바꿈이 글자로 들어온 PKCS#8 키도 읽는다`() {
        // given
        val escaped = TestRsaKeys.pkcs8Pem().replace("\n", "\\n")
        val credentials = GitHubAppCredentials(properties(escaped), clock)

        // when
        val jwt = credentials.appJwt()

        // then
        Jwts.parser().verifyWith(TestRsaKeys.keyPair.public as RSAPublicKey)
            .clock { java.util.Date.from(clock.instant()) }
            .build()
            .parseSignedClaims(jwt).payload.issuer shouldBe "5531779"
    }

    @Test
    fun `JWT 는 만료 1분 전까지 다시 쓰고 그 뒤에는 새로 서명한다`() {
        // given
        val credentials = GitHubAppCredentials(properties(TestRsaKeys.pkcs1Pem()), clock)
        val first = credentials.appJwt()

        // when
        clock.advance(Duration.ofMinutes(7))
        val reused = credentials.appJwt()
        clock.advance(Duration.ofMinutes(1))
        val renewed = credentials.appJwt()

        // then
        reused shouldBe first
        renewed shouldNotBe first
    }

    @Test
    fun `설치 토큰은 남은 시간이 30분보다 길 때만 다시 준다`() {
        // given
        val credentials = GitHubAppCredentials(properties(TestRsaKeys.pkcs1Pem()), clock)
        val repository = RepoName("acme", "manifests")
        credentials.remember(7, RepoToken("ghs_a", now.plus(Duration.ofHours(1)), 42, repository))

        // when
        val fresh = credentials.cachedRepoToken(7, 42)
        val byName = credentials.cachedRepoToken(7, repository)
        val otherInstallation = credentials.cachedRepoToken(8, 42)
        clock.advance(Duration.ofMinutes(31))
        val stale = credentials.cachedRepoToken(7, 42)

        // then
        fresh?.token shouldBe "ghs_a"
        byName?.token shouldBe "ghs_a"
        otherInstallation.shouldBeNull()
        stale.shouldBeNull()
    }

    private fun properties(privateKey: String) = GitHubAppProperties(
        id = 5531779,
        slug = "kukie-gitops",
        privateKey = privateKey,
        apiUrl = "https://api.github.com",
        webUrl = "https://github.com",
    )

    class MutableClock(private var instant: Instant) : Clock() {
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }

        override fun instant(): Instant = instant

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?): Clock = this
    }
}
