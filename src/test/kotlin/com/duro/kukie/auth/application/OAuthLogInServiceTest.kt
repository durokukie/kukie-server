package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.presentation.dto.request.OAuthLogInRequest
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class OAuthLogInServiceTest {

    @MockK
    private lateinit var oAuthClient: OAuthClient

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockK(relaxUnitFun = true)
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @InjectMockKs
    private lateinit var oAuthLogInService: OAuthLogInService

    private val provider = OAuthProvider.GITHUB
    private val request = OAuthLogInRequest("auth-code", "http://127.0.0.1:8080/callback")
    private val profile = OAuthProfile(email = UserFixture.DEFAULT_EMAIL, name = UserFixture.DEFAULT_NAME)

    @Test
    fun `기존 사용자면 가입 없이 로그인하고 토큰을 발급한다`() {
        // given
        val user = UserFixture.user()
        every { oAuthClient.fetchProfile(provider, request.code, request.redirectUri, request.codeVerifier) } returns profile
        every { userRepository.findByEmail(profile.email) } returns user
        every { jwtTokenProvider.generateAccessToken(user.id) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(user.id) } returns "refresh-token"

        // when
        val response = oAuthLogInService(provider, request)

        // then
        response.accessToken shouldBe "access-token"
        response.refreshToken shouldBe "refresh-token"
        verify { refreshTokenRepository.save(user.id, "refresh-token") }
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `가입되지 않은 이메일이면 자동으로 가입한 뒤 토큰을 발급한다`() {
        // given
        val savedUser = slot<User>()
        every { oAuthClient.fetchProfile(provider, request.code, request.redirectUri, request.codeVerifier) } returns profile
        every { userRepository.findByEmail(profile.email) } returns null
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { jwtTokenProvider.generateAccessToken(any()) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(any()) } returns "refresh-token"

        // when
        val response = oAuthLogInService(provider, request)

        // then
        response.accessToken shouldBe "access-token"
        response.refreshToken shouldBe "refresh-token"
        with(savedUser.captured) {
            name shouldBe profile.name
            email shouldBe profile.email
            password shouldBe null
        }
        verify { refreshTokenRepository.save(savedUser.captured.id, "refresh-token") }
    }

    @Test
    fun `이름이 50자를 넘으면 50자로 잘라서 가입한다`() {
        // given
        val savedUser = slot<User>()
        every { oAuthClient.fetchProfile(provider, request.code, request.redirectUri, request.codeVerifier) } returns
            profile.copy(name = "a".repeat(60))
        every { userRepository.findByEmail(profile.email) } returns null
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { jwtTokenProvider.generateAccessToken(any()) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(any()) } returns "refresh-token"

        // when
        oAuthLogInService(provider, request)

        // then
        savedUser.captured.name shouldBe "a".repeat(50)
    }
}
