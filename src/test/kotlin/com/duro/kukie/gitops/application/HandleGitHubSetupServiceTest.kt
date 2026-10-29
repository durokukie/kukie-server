package com.duro.kukie.gitops.application

import com.duro.kukie.gitops.application.port.`in`.GitHubSetupCommand
import com.duro.kukie.gitops.application.port.out.GitHubAppGateway
import com.duro.kukie.gitops.application.port.out.GitHubInstallationInfo
import com.duro.kukie.gitops.application.port.out.InstallStateCodec
import com.duro.kukie.gitops.domain.GitHubAccount
import com.duro.kukie.gitops.domain.GitHubInstallRequestRepository
import com.duro.kukie.gitops.domain.GitHubInstallation
import com.duro.kukie.gitops.domain.GitHubInstallationRepository
import com.duro.kukie.gitops.domain.InstallState
import com.duro.kukie.gitops.domain.InstallStateRepository
import com.duro.kukie.gitops.domain.RepoName
import com.duro.kukie.gitops.exception.InstallationNotVerifiedException
import com.duro.kukie.global.config.properties.GitHubAppProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** 설치 때 사용자 인증(OAuth)을 켠 경우. 꺼진 경우는 GitHubAppIntegrationTest 가 본다. */
@ExtendWith(MockKExtension::class)
class HandleGitHubSetupServiceTest {

    @MockK
    private lateinit var installStateCodec: InstallStateCodec

    @MockK
    private lateinit var installStateRepository: InstallStateRepository

    @MockK
    private lateinit var gitHubAppGateway: GitHubAppGateway

    @MockK
    private lateinit var gitHubInstallationRepository: GitHubInstallationRepository

    @MockK(relaxUnitFun = true)
    private lateinit var gitHubInstallRequestRepository: GitHubInstallRequestRepository

    private lateinit var handleGitHubSetupService: HandleGitHubSetupService

    private val teamId: UUID = UUID.randomUUID()
    private val userId: UUID = UUID.randomUUID()
    private val now = Instant.now()
    private val state = InstallState(teamId, userId, "nonce", now, now.plus(Duration.ofMinutes(30)))

    @BeforeEach
    fun setUp() {
        val properties = GitHubAppProperties(
            id = 5531779,
            slug = "kukie-gitops",
            privateKey = "unused",
            apiUrl = "https://api.github.com",
            webUrl = "https://github.com",
            clientId = "Iv23li-client",
            clientSecret = "client-secret",
        )
        handleGitHubSetupService = HandleGitHubSetupService(
            installStateCodec,
            installStateRepository,
            gitHubAppGateway,
            gitHubInstallationRepository,
            gitHubInstallRequestRepository,
            properties,
        )
        every { installStateCodec.decode("state") } returns state
        every { installStateRepository.take("nonce") } returns teamId
        every { gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID) } returns null
    }

    @Test
    fun `설치한 사람이 접근할 수 있는 설치면 전부터 있던 설치도 팀에 묶는다`() {
        // given
        every { gitHubAppGateway.userInstallationIds("code") } returns setOf(INSTALLATION_ID, 99)
        every { gitHubAppGateway.findInstallation(INSTALLATION_ID) } returns info(createdAt = now.minus(Duration.ofDays(30)))
        every { gitHubAppGateway.installationRepositories(INSTALLATION_ID) } returns listOf(RepoName("acme", "manifests"))
        val saved = slot<GitHubInstallation>()
        every { gitHubInstallationRepository.saveAndFlush(capture(saved)) } answers { saved.captured }

        // when
        val result = handleGitHubSetupService(GitHubSetupCommand(INSTALLATION_ID, "install", "state", "code"))

        // then
        result shouldBe GitHubSetupResult(GitHubSetupResult.Outcome.INSTALLED, teamId)
        saved.captured.teamId shouldBe teamId
        saved.captured.repositories shouldBe listOf("acme/manifests")
    }

    @Test
    fun `설치한 사람이 접근할 수 없는 설치는 묶지 않는다`() {
        // given
        every { gitHubAppGateway.userInstallationIds("code") } returns setOf(99)

        // when & then
        shouldThrow<InstallationNotVerifiedException> {
            handleGitHubSetupService(GitHubSetupCommand(INSTALLATION_ID, "install", "state", "code"))
        }
        verify(exactly = 0) { gitHubAppGateway.findInstallation(any()) }
        verify(exactly = 0) { gitHubInstallationRepository.saveAndFlush(any()) }
    }

    @Test
    fun `사용자 인증 code 가 없으면 묶지 않는다`() {
        // when & then
        shouldThrow<InstallationNotVerifiedException> {
            handleGitHubSetupService(GitHubSetupCommand(INSTALLATION_ID, "install", "state", null))
        }
        verify(exactly = 0) { gitHubAppGateway.userInstallationIds(any()) }
    }

    private fun info(createdAt: Instant) = GitHubInstallationInfo(
        installationId = INSTALLATION_ID,
        account = GitHubAccount("acme", "Organization"),
        repositorySelection = "selected",
        suspended = false,
        createdAt = createdAt,
    )

    companion object {
        private const val INSTALLATION_ID = 7L
    }
}
