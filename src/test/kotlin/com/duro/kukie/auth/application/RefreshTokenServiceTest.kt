package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.global.security.JwtTokenProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MockKExtension::class)
class RefreshTokenServiceTest {

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockK(relaxUnitFun = true)
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @InjectMockKs
    private lateinit var refreshTokenService: RefreshTokenService

    private val userId: UUID = UUID.randomUUID()
    private val request = RefreshTokenRequest("refresh-token")

    @Test
    fun `재발급에 성공하면 새 토큰을 발급하고 기존 리프레시 토큰을 삭제한다`() {
        // given
        every { jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken) } returns userId
        every { refreshTokenRepository.findByUserId(userId) } returns request.refreshToken
        every { jwtTokenProvider.generateAccessToken(userId) } returns "new-access-token"
        every { jwtTokenProvider.generateRefreshToken(userId) } returns "new-refresh-token"

        // when
        val response = refreshTokenService(request)

        // then
        response.accessToken shouldBe "new-access-token"
        response.refreshToken shouldBe "new-refresh-token"
        verify { refreshTokenRepository.save(userId, "new-refresh-token") }
    }

    @Test
    fun `유효하지 않은 리프레시 토큰이면 예외가 발생한다`() {
        // given
        every { jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken) } returns null

        // when & then
        shouldThrow<InvalidTokenException> { refreshTokenService(request) }
    }

    @Test
    fun `만료된 리프레시 토큰의 재사용이 감지되면 현재 리프레시 토큰을 삭제해 로그아웃 시킨다`() {
        // given
        every { jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken) } returns userId
        every { refreshTokenRepository.findByUserId(userId) } returns "other-refresh-token"

        // when & then
        shouldThrow<InvalidTokenException> { refreshTokenService(request) }

        verify { refreshTokenRepository.deleteByUserId(userId) }
    }
}
