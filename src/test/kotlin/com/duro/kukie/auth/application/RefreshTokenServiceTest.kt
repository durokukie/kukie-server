package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.domain.RefreshTokenRotation
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.global.security.TokenSubject
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MockKExtension::class)
class RefreshTokenServiceTest {

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockK(relaxUnitFun = true)
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK
    private lateinit var logInSessions: LogInSessions

    @InjectMockKs
    private lateinit var refreshTokenService: RefreshTokenService

    private val userId: UUID = UUID.randomUUID()
    private val sessionId = "session-1"
    private val request = RefreshTokenRequest("refresh-token")

    @BeforeEach
    fun setUp() {
        every { jwtTokenProvider.generateAccessToken(userId, sessionId) } returns "new-access-token"
        every { jwtTokenProvider.generateRefreshToken(userId, sessionId) } returns "new-refresh-token"
    }

    @Test
    fun `재발급에 성공하면 그 세션의 리프레시 토큰을 새 것으로 바꾼다`() {
        every { jwtTokenProvider.parseRefreshToken(request.refreshToken) } returns TokenSubject(userId, sessionId)
        every { refreshTokenRepository.rotate(userId, sessionId, "refresh-token", "new-refresh-token") } returns
            RefreshTokenRotation.Rotated

        val response = refreshTokenService(request)

        response shouldBe TokenResponse("new-access-token", "new-refresh-token")
    }

    @Test
    fun `직전 토큰이 겹침 시간 안에 다시 오면 지금 리프레시 토큰을 그대로 준다`() {
        every { jwtTokenProvider.parseRefreshToken(request.refreshToken) } returns TokenSubject(userId, sessionId)
        every { refreshTokenRepository.rotate(userId, sessionId, any(), any()) } returns
            RefreshTokenRotation.RecentlyRotated("current-refresh-token")

        val response = refreshTokenService(request)

        response shouldBe TokenResponse("new-access-token", "current-refresh-token")
    }

    @Test
    fun `이미 쓴 토큰이거나 세션이 없으면 예외가 발생한다`() {
        every { jwtTokenProvider.parseRefreshToken(request.refreshToken) } returns TokenSubject(userId, sessionId)
        every { refreshTokenRepository.rotate(userId, sessionId, any(), any()) } returnsMany
            listOf(RefreshTokenRotation.Reused, RefreshTokenRotation.Missing)

        shouldThrow<InvalidTokenException> { refreshTokenService(request) }
        shouldThrow<InvalidTokenException> { refreshTokenService(request) }
    }

    @Test
    fun `리프레시 토큰이 유효하지 않으면 예외가 발생한다`() {
        every { jwtTokenProvider.parseRefreshToken(request.refreshToken) } returns null

        shouldThrow<InvalidTokenException> { refreshTokenService(request) }
    }

    @Test
    fun `세션 id 없는 옛 토큰이 옛 키와 맞으면 새 세션으로 옮긴다`() {
        every { jwtTokenProvider.parseRefreshToken(request.refreshToken) } returns TokenSubject(userId, null)
        every { refreshTokenRepository.takeLegacy(userId, "refresh-token") } returns true
        every { logInSessions.open(userId) } returns TokenResponse("a", "r")

        refreshTokenService(request) shouldBe TokenResponse("a", "r")
    }

    @Test
    fun `세션 id 없는 옛 토큰이 옛 키와 다르면 예외가 발생하고 새 세션을 열지 않는다`() {
        every { jwtTokenProvider.parseRefreshToken(request.refreshToken) } returns TokenSubject(userId, null)
        every { refreshTokenRepository.takeLegacy(userId, "refresh-token") } returns false

        shouldThrow<InvalidTokenException> { refreshTokenService(request) }
        verify(exactly = 0) { logInSessions.open(any()) }
    }
}
