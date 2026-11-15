package com.duro.kukie.global.security

import com.duro.kukie.auth.exception.ExpiredTokenException
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.global.config.properties.JwtProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class JwtTokenProviderTest {

    private val jwtTokenProvider = JwtTokenProvider(
        JwtProperties(
            secret = "jwt-secret-key-must-be-at-least-32-bytes",
            accessTokenExpiration = Duration.ofMinutes(30),
            refreshTokenExpiration = Duration.ofDays(30),
        )
    )

    private val userId: UUID = UUID.randomUUID()

    @Test
    fun `액세스 토큰에서 사용자 id를 추출할 수 있다`() {
        val token = jwtTokenProvider.generateAccessToken(userId)

        jwtTokenProvider.getUserIdFromAccessToken(token) shouldBe userId
    }

    @Test
    fun `리프레시 토큰에서 사용자 id와 세션 id를 추출할 수 있다`() {
        val token = jwtTokenProvider.generateRefreshToken(userId, "session-1")

        jwtTokenProvider.parseRefreshToken(token) shouldBe TokenSubject(userId, "session-1")
    }

    @Test
    fun `액세스 토큰에도 세션 id 를 담는다`() {
        val token = jwtTokenProvider.generateAccessToken(userId, "session-1")

        jwtTokenProvider.parseAccessToken(token) shouldBe TokenSubject(userId, "session-1")
    }

    @Test
    fun `같은 사용자라도 매번 다른 토큰이 발급된다`() {
        val first = jwtTokenProvider.generateRefreshToken(userId, "session-1")
        val second = jwtTokenProvider.generateRefreshToken(userId, "session-1")

        first shouldNotBe second
    }

    @Test
    fun `토큰 타입이 다르면 사용자 id를 추출할 수 없다`() {
        val accessToken = jwtTokenProvider.generateAccessToken(userId)
        val refreshToken = jwtTokenProvider.generateRefreshToken(userId, "session-1")

        jwtTokenProvider.parseRefreshToken(accessToken).shouldBeNull()
        jwtTokenProvider.getUserIdFromAccessToken(refreshToken).shouldBeNull()
    }

    @Test
    fun `만료된 토큰이면 예외가 발생한다`() {
        val expiredTokenProvider = JwtTokenProvider(
            JwtProperties(
                secret = "jwt-secret-key-must-be-at-least-32-bytes",
                accessTokenExpiration = Duration.ofMinutes(-1),
                refreshTokenExpiration = Duration.ofMinutes(-1),
            )
        )
        val expiredToken = expiredTokenProvider.generateAccessToken(userId)

        shouldThrow<ExpiredTokenException> { jwtTokenProvider.getUserIdFromAccessToken(expiredToken) }
    }

    @Test
    fun `형식이 잘못된 토큰이면 예외가 발생한다`() {
        shouldThrow<InvalidTokenException> { jwtTokenProvider.getUserIdFromAccessToken("not-a-jwt") }
    }

    @Test
    fun `다른 키로 서명된 토큰이면 예외가 발생한다`() {
        val otherKeyProvider = JwtTokenProvider(
            JwtProperties(
                secret = "another-secret-key-that-is-32-bytes!",
                accessTokenExpiration = Duration.ofMinutes(30),
                refreshTokenExpiration = Duration.ofDays(30),
            )
        )
        val token = otherKeyProvider.generateAccessToken(userId)

        shouldThrow<InvalidTokenException> { jwtTokenProvider.getUserIdFromAccessToken(token) }
    }
}
