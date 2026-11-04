package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.OAuthLogInCommand
import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.global.domain.Email
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

    private val command = OAuthLogInCommand(OAuthProvider.GITHUB, "auth-code", "http://127.0.0.1:8080/callback", null)
    private val profile = OAuthProfile(email = UserFixture.DEFAULT_EMAIL, name = UserFixture.DEFAULT_NAME)

    @Test
    fun `기존 사용자면 가입 없이 로그인하고 토큰을 발급한다`() {
        // given
        val user = UserFixture.user()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { jwtTokenProvider.generateAccessToken(user.id) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(user.id) } returns "refresh-token"

        // when
        val response = oAuthLogInService(command)

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
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByEmail(Email(profile.email)) } returns null
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { jwtTokenProvider.generateAccessToken(any()) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(any()) } returns "refresh-token"

        // when
        val response = oAuthLogInService(command)

        // then
        response.accessToken shouldBe "access-token"
        response.refreshToken shouldBe "refresh-token"
        with(savedUser.captured) {
            name shouldBe profile.name
            email shouldBe Email(profile.email)
            password shouldBe null
        }
        verify { refreshTokenRepository.save(savedUser.captured.id, "refresh-token") }
    }

    @Test
    fun `기존 사용자의 프로필 사진이 바뀌었으면 새 주소로 저장한다`() {
        // given
        val user = UserFixture.user()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(avatarUrl = AVATAR_URL)
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { userRepository.save(user) } returns user
        every { jwtTokenProvider.generateAccessToken(user.id) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(user.id) } returns "refresh-token"

        // when
        oAuthLogInService(command)

        // then
        user.avatarUrl shouldBe AVATAR_URL
        verify { userRepository.save(user) }
    }

    @Test
    fun `사진을 주지 않는 로그인 수단으로 들어오면 있던 사진을 그대로 둔다`() {
        // given
        val user = UserFixture.user().apply { changeAvatar(AVATAR_URL) }
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { jwtTokenProvider.generateAccessToken(user.id) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(user.id) } returns "refresh-token"

        // when
        oAuthLogInService(command)

        // then
        user.avatarUrl shouldBe AVATAR_URL
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `가입할 때 프로필 사진 주소를 함께 저장한다`() {
        // given
        val savedUser = slot<User>()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(avatarUrl = AVATAR_URL)
        every { userRepository.findByEmail(Email(profile.email)) } returns null
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { jwtTokenProvider.generateAccessToken(any()) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(any()) } returns "refresh-token"

        // when
        oAuthLogInService(command)

        // then
        savedUser.captured.avatarUrl shouldBe AVATAR_URL
    }

    @Test
    fun `https 가 아닌 프로필 사진 주소는 저장하지 않는다`() {
        // given
        val savedUser = slot<User>()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(avatarUrl = "javascript:alert(1)")
        every { userRepository.findByEmail(Email(profile.email)) } returns null
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { jwtTokenProvider.generateAccessToken(any()) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(any()) } returns "refresh-token"

        // when
        oAuthLogInService(command)

        // then
        savedUser.captured.avatarUrl shouldBe null
    }

    @Test
    fun `이름이 50자를 넘으면 50자로 잘라서 가입한다`() {
        // given
        val savedUser = slot<User>()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(name = "a".repeat(60))
        every { userRepository.findByEmail(Email(profile.email)) } returns null
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { jwtTokenProvider.generateAccessToken(any()) } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(any()) } returns "refresh-token"

        // when
        oAuthLogInService(command)

        // then
        savedUser.captured.name shouldBe "a".repeat(50)
    }

    companion object {
        private const val AVATAR_URL = "https://avatars.githubusercontent.com/u/1"
    }
}
