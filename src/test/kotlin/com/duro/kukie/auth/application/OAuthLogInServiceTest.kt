package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.OAuthLogInCommand
import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.presentation.dto.response.TokenResponse
import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException

@ExtendWith(MockKExtension::class)
class OAuthLogInServiceTest {

    @MockK
    private lateinit var oAuthClient: OAuthClient

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var logInSessions: LogInSessions

    @MockK
    private lateinit var transactionTemplate: TransactionTemplate

    @InjectMockKs
    private lateinit var oAuthLogInService: OAuthLogInService

    private val command = OAuthLogInCommand(OAuthProvider.GITHUB, "auth-code", "http://127.0.0.1:8080/callback", null)
    private val profile = OAuthProfile(email = UserFixture.DEFAULT_EMAIL, name = UserFixture.DEFAULT_NAME)

    @BeforeEach
    fun setUp() {
        every { transactionTemplate.execute(any<TransactionCallback<Any>>()) } answers {
            firstArg<TransactionCallback<Any>>().doInTransaction(mockk())
        }
    }

    @Test
    fun `기존 사용자면 가입 없이 로그인하고 토큰을 발급한다`() {
        // given
        val user = UserFixture.user()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { logInSessions.open(user.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        val response = oAuthLogInService(command)

        // then
        response.accessToken shouldBe "access-token"
        response.refreshToken shouldBe "refresh-token"
        verify { logInSessions.open(user.id) }
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `가입되지 않은 이메일이면 자동으로 가입한 뒤 토큰을 발급한다`() {
        // given
        val savedUser = slot<User>()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByEmail(Email(profile.email)) } returns null
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { logInSessions.open(any()) } returns TokenResponse("access-token", "refresh-token")

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
        verify { logInSessions.open(savedUser.captured.id) }
    }

    @Test
    fun `기존 사용자의 프로필 사진이 바뀌었으면 새 주소로 저장한다`() {
        // given
        val user = UserFixture.user()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(avatarUrl = AVATAR_URL)
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { userRepository.lockById(user.id) } returns user
        every { userRepository.saveAndFlush(user) } returns user
        every { logInSessions.open(user.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        oAuthLogInService(command)

        // then
        user.avatarUrl shouldBe AVATAR_URL
        verify { userRepository.saveAndFlush(user) }
    }

    @Test
    fun `사진을 주지 않는 로그인 수단으로 들어오면 있던 사진을 그대로 둔다`() {
        // given
        val user = UserFixture.user().apply { changeAvatar(AVATAR_URL) }
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { logInSessions.open(user.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        oAuthLogInService(command)

        // then
        user.avatarUrl shouldBe AVATAR_URL
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `GitHub 로그인이면 기존 사용자에 GitHub 계정을 연결한다`() {
        // given
        val user = UserFixture.user()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(gitHub = GITHUB)
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { userRepository.existsByGithubId(GITHUB.id) } returns false
        every { userRepository.lockById(user.id) } returns user
        every { userRepository.saveAndFlush(user) } returns user
        every { logInSessions.open(user.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        oAuthLogInService(command)

        // then
        user.githubId shouldBe GITHUB.id
        user.githubLogin shouldBe GITHUB.login
        verify { userRepository.saveAndFlush(user) }
    }

    @Test
    fun `같은 GitHub 계정으로 다시 로그인하면 바뀐 계정명만 맞춘다`() {
        // given
        val user = UserFixture.user().apply { linkGitHub(GITHUB) }
        val renamed = GITHUB.copy(login = "new-login")
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(gitHub = renamed)
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { userRepository.lockById(user.id) } returns user
        every { userRepository.saveAndFlush(user) } returns user
        every { logInSessions.open(user.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        oAuthLogInService(command)

        // then
        user.githubId shouldBe GITHUB.id
        user.githubLogin shouldBe "new-login"
        verify(exactly = 0) { userRepository.existsByGithubId(any()) }
    }

    @Test
    fun `다른 회원이 쓰는 GitHub 계정이면 연결하지 않고 로그인한다`() {
        // given
        val user = UserFixture.user()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(gitHub = GITHUB)
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { userRepository.existsByGithubId(GITHUB.id) } returns true
        every { logInSessions.open(user.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        val response = oAuthLogInService(command)

        // then
        response.accessToken shouldBe "access-token"
        user.githubId shouldBe null
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `이미 다른 GitHub 계정이 연결된 회원은 로그인으로 바꾸지 않는다`() {
        // given
        val user = UserFixture.user().apply { linkGitHub(GITHUB) }
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(gitHub = GITHUB.copy(id = 999, login = "someone-else"))
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { logInSessions.open(user.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        oAuthLogInService(command)

        // then
        user.githubId shouldBe GITHUB.id
        user.githubLogin shouldBe GITHUB.login
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `GitHub 로 가입하면 GitHub 계정을 함께 저장한다`() {
        // given
        val savedUser = slot<User>()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(gitHub = GITHUB)
        every { userRepository.findByEmail(Email(profile.email)) } returns null
        every { userRepository.existsByGithubId(GITHUB.id) } returns false
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { logInSessions.open(any()) } returns TokenResponse("access-token", "refresh-token")

        // when
        oAuthLogInService(command)

        // then
        savedUser.captured.githubId shouldBe GITHUB.id
        savedUser.captured.githubLogin shouldBe GITHUB.login
    }

    @Test
    fun `GitHub 가 아닌 로그인은 있던 GitHub 연결을 그대로 둔다`() {
        // given
        val user = UserFixture.user().apply { linkGitHub(GITHUB) }
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByEmail(Email(profile.email)) } returns user
        every { logInSessions.open(user.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        oAuthLogInService(command)

        // then
        user.githubId shouldBe GITHUB.id
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `GitHub 계정명은 39자까지만 저장한다`() {
        // given
        val user = UserFixture.user()

        // when
        user.linkGitHub(GITHUB.copy(login = "a".repeat(50)))

        // then
        user.githubLogin shouldBe "a".repeat(39)
    }

    @Test
    fun `가입하는 사이 다른 회원이 같은 GitHub 계정을 연결하면 연결 없이 가입한다`() {
        // given
        val saved = mutableListOf<User>()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(gitHub = GITHUB)
        every { userRepository.findByEmail(Email(profile.email)) } returns null
        every { userRepository.existsByGithubId(GITHUB.id) } returns false
        every { userRepository.saveAndFlush(capture(saved)) } throws
            violation(User.GITHUB_ID_UNIQUE_CONSTRAINT) andThenAnswer { saved.last() }
        every { logInSessions.open(any()) } returns TokenResponse("access-token", "refresh-token")

        // when
        val response = oAuthLogInService(command)

        // then
        response.accessToken shouldBe "access-token"
        saved.size shouldBe 2
        saved.last().githubId shouldBe null
    }

    @Test
    fun `로그인하는 사이 다른 회원이 같은 GitHub 계정을 연결하면 연결 없이 로그인한다`() {
        // given
        val user = UserFixture.user()
        val stored = UserFixture.user()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(gitHub = GITHUB)
        every { userRepository.findByEmail(Email(profile.email)) } returns user andThen stored
        every { userRepository.existsByGithubId(GITHUB.id) } returns false
        every { userRepository.lockById(user.id) } returns user
        every { userRepository.saveAndFlush(user) } throws violation(User.GITHUB_ID_UNIQUE_CONSTRAINT)
        every { logInSessions.open(stored.id) } returns TokenResponse("access-token", "refresh-token")

        // when
        val response = oAuthLogInService(command)

        // then
        response.accessToken shouldBe "access-token"
        verify { logInSessions.open(stored.id) }
    }

    @Test
    fun `가입할 때 프로필 사진 주소를 함께 저장한다`() {
        // given
        val savedUser = slot<User>()
        every { oAuthClient.fetchProfile(command.provider, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(avatarUrl = AVATAR_URL)
        every { userRepository.findByEmail(Email(profile.email)) } returns null
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }
        every { logInSessions.open(any()) } returns TokenResponse("access-token", "refresh-token")

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
        every { logInSessions.open(any()) } returns TokenResponse("access-token", "refresh-token")

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
        every { logInSessions.open(any()) } returns TokenResponse("access-token", "refresh-token")

        // when
        oAuthLogInService(command)

        // then
        savedUser.captured.name shouldBe "a".repeat(50)
    }

    private fun violation(constraint: String) =
        DataIntegrityViolationException("duplicate", ConstraintViolationException("duplicate", SQLException(), constraint))

    companion object {
        private val GITHUB = GitHubAccount(id = 583231, login = "octocat")
        private const val AVATAR_URL = "https://avatars.githubusercontent.com/u/1"
    }
}
