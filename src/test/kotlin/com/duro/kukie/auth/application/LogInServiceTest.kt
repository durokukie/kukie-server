package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.exception.InvalidCredentialsException
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockKExtension::class)
class LogInServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @SpyK
    private var passwordEncoder = BCryptPasswordEncoder()

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockK(relaxUnitFun = true)
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @InjectMockKs
    private lateinit var logInService: LogInService

    private val request = LogInRequest("test@example.com", "password123")

    @Test
    fun `로그인에 성공하면 토큰을 발급하고 리프레시 토큰을 저장한다`() {
        // given
        val user = UserFixture.user()
        every { userRepository.findByEmail(request.email) } returns user
        every { jwtTokenProvider.generateAccessToken(user.id) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(user.id) } returns "refresh-token"

        // when
        val response = logInService(request)

        // then
        response.accessToken shouldBe "access-token"
        response.refreshToken shouldBe "refresh-token"
        verify { refreshTokenRepository.save(user.id, "refresh-token") }
    }

    @Test
    fun `존재하지 않는 이메일이면 예외가 발생한다`() {
        // given
        every { userRepository.findByEmail(request.email) } returns null

        // when & then
        shouldThrow<InvalidCredentialsException> { logInService(request) }
    }

    @Test
    fun `비밀번호가 일치하지 않으면 예외가 발생한다`() {
        // given
        val request = request.copy(password = "wrong-password")
        every { userRepository.findByEmail(request.email) } returns UserFixture.user()

        // when & then
        shouldThrow<InvalidCredentialsException> { logInService(request) }
    }
}
