package com.duro.kukie.gitops.infrastructure

import com.duro.kukie.gitops.application.port.out.RepoToken
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.global.config.properties.GitHubAppProperties
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.security.PrivateKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * GitHub App 개인키를 쓰는 유일한 곳. App JWT(RS256, 10분)를 서명하고, 레포 하나로 좁힌 설치 토큰을 기억한다.
 * 토큰 값은 로그에 남기지 않는다.
 */
@Component
class GitHubAppCredentials(
    private val properties: GitHubAppProperties,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val privateKey: PrivateKey by lazy { GitHubAppPrivateKey.parse(properties.privateKey) }

    @Volatile
    private var appJwt: SignedJwt? = null

    private val repoTokens = ConcurrentHashMap<TokenKey, RepoToken>()

    fun appJwt(): String {
        val now = clock.instant()
        appJwt?.takeIf { it.expiresAt.isAfter(now + JWT_RENEW_BEFORE) }?.let { return it.value }

        // GitHub 와 시계가 어긋나도 받도록 발급 시각을 1분 당긴다. 수명은 GitHub 상한인 10분
        val issuedAt = now - CLOCK_SKEW
        val expiresAt = issuedAt + JWT_LIFETIME
        val value = Jwts.builder()
            .issuer(properties.id.toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
        appJwt = SignedJwt(value, expiresAt)

        return value
    }

    fun cachedRepoToken(installationId: Long, repositoryId: Long): RepoToken? =
        repoTokens[TokenKey(installationId, repositoryId)]?.takeIf(::reusable)

    fun cachedRepoToken(installationId: Long, repository: RepoName): RepoToken? =
        repoTokens.entries
            .firstOrNull { (key, token) -> key.installationId == installationId && token.repository == repository }
            ?.value
            ?.takeIf(::reusable)

    fun remember(installationId: Long, token: RepoToken) {
        repoTokens[TokenKey(installationId, token.repositoryId)] = token
    }

    fun forget(installationId: Long, repositoryId: Long): RepoToken? =
        repoTokens.remove(TokenKey(installationId, repositoryId))

    /** 받은 쪽이 한동안 쓸 수 있게 남은 시간이 넉넉할 때만 다시 준다. */
    private fun reusable(token: RepoToken): Boolean = token.expiresAt.isAfter(clock.instant() + TOKEN_REUSE_MIN)

    private class SignedJwt(val value: String, val expiresAt: Instant)

    private data class TokenKey(val installationId: Long, val repositoryId: Long)

    companion object {
        private val CLOCK_SKEW: Duration = Duration.ofSeconds(60)
        private val JWT_LIFETIME: Duration = Duration.ofMinutes(10)
        private val JWT_RENEW_BEFORE: Duration = Duration.ofMinutes(1)
        val TOKEN_REUSE_MIN: Duration = Duration.ofMinutes(30)
    }
}
