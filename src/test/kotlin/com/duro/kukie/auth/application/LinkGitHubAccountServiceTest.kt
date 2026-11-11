package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.LinkGitHubAccountCommand
import com.duro.kukie.auth.application.port.out.OAuthClient
import com.duro.kukie.auth.application.port.out.OAuthProfile
import com.duro.kukie.auth.domain.OAuthProvider
import com.duro.kukie.auth.exception.GitHubAccountTakenException
import com.duro.kukie.auth.exception.GitHubAlreadyLinkedException
import com.duro.kukie.auth.exception.GitHubLinkFailedException
import com.duro.kukie.auth.exception.OAuthLogInFailedException
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException

@ExtendWith(MockKExtension::class)
class LinkGitHubAccountServiceTest {

    @MockK
    private lateinit var oAuthClient: OAuthClient

    @MockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var linkGitHubAccountService: LinkGitHubAccountService

    private val user = UserFixture.oAuthUser()
    private val command = LinkGitHubAccountCommand(user.id, "auth-code", "http://127.0.0.1:8080/callback", "verifier")
    private val gitHub = GitHubAccount(id = 583231, login = "octocat")
    private val profile = OAuthProfile(email = "other@example.com", name = "옥토캣", gitHub = gitHub)

    @Test
    fun `GitHub 계정을 회원에 연결한다`() {
        // given
        every { oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByIdOrThrow(user.id) } returns user
        every { userRepository.unlinkGitHubFromOthers(gitHub.id, user.id) } returns 0
        every { userRepository.saveAndFlush(user) } returns user

        // when
        val response = linkGitHubAccountService(command)

        // then
        response.githubLogin shouldBe "octocat"
        user.githubId shouldBe gitHub.id
        user.email.value shouldBe UserFixture.DEFAULT_EMAIL
    }

    @Test
    fun `다른 회원에 연결돼 있던 GitHub 계정은 이 회원으로 옮긴다`() {
        // given
        every { oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByIdOrThrow(user.id) } returns user
        every { userRepository.unlinkGitHubFromOthers(gitHub.id, user.id) } returns 1
        every { userRepository.saveAndFlush(user) } returns user

        // when
        linkGitHubAccountService(command)

        // then
        user.githubId shouldBe gitHub.id
        verifyOrder {
            userRepository.unlinkGitHubFromOthers(gitHub.id, user.id)
            userRepository.saveAndFlush(user)
        }
    }

    @Test
    fun `같은 GitHub 계정을 다시 연결하면 바뀐 계정명을 맞춘다`() {
        // given
        user.linkGitHub(gitHub.copy(login = "old-login"))
        every { oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByIdOrThrow(user.id) } returns user
        every { userRepository.unlinkGitHubFromOthers(gitHub.id, user.id) } returns 0
        every { userRepository.saveAndFlush(user) } returns user

        // when
        val response = linkGitHubAccountService(command)

        // then
        response.githubLogin shouldBe "octocat"
    }

    @Test
    fun `이미 다른 GitHub 계정이 연결된 회원은 연결을 끊기 전에는 연결할 수 없다`() {
        // given
        user.linkGitHub(GitHubAccount(id = 1, login = "old"))
        every { oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByIdOrThrow(user.id) } returns user

        // when & then
        shouldThrow<GitHubAlreadyLinkedException> { linkGitHubAccountService(command) }
        user.githubId shouldBe 1
        verify(exactly = 0) { userRepository.unlinkGitHubFromOthers(any(), any()) }
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `옮기는 사이 다른 회원이 같은 GitHub 계정을 연결하면 연결할 수 없다`() {
        // given
        every { oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByIdOrThrow(user.id) } returns user
        every { userRepository.unlinkGitHubFromOthers(gitHub.id, user.id) } returns 0
        every { userRepository.saveAndFlush(user) } throws violation(User.GITHUB_ID_UNIQUE_CONSTRAINT)

        // when & then
        shouldThrow<GitHubAccountTakenException> { linkGitHubAccountService(command) }
    }

    @Test
    fun `GitHub 계정과 무관한 저장 오류는 그대로 던진다`() {
        // given
        every { oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier) } returns profile
        every { userRepository.findByIdOrThrow(user.id) } returns user
        every { userRepository.unlinkGitHubFromOthers(gitHub.id, user.id) } returns 0
        every { userRepository.saveAndFlush(user) } throws violation(User.EMAIL_UNIQUE_CONSTRAINT)

        // when & then
        shouldThrow<DataIntegrityViolationException> { linkGitHubAccountService(command) }
    }

    @Test
    fun `인가 코드가 유효하지 않으면 연결에 실패한다`() {
        // given
        every { oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier) } throws
            OAuthLogInFailedException()

        // when & then
        shouldThrow<GitHubLinkFailedException> { linkGitHubAccountService(command) }
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `GitHub 계정 정보를 받지 못하면 연결에 실패한다`() {
        // given
        every { oAuthClient.fetchProfile(OAuthProvider.GITHUB, command.code, command.redirectUri, command.codeVerifier) } returns
            profile.copy(gitHub = null)

        // when & then
        shouldThrow<GitHubLinkFailedException> { linkGitHubAccountService(command) }
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    private fun violation(constraint: String) =
        DataIntegrityViolationException("duplicate", ConstraintViolationException("duplicate", SQLException(), constraint))
}
