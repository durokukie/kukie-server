package com.duro.kukie.gitops

import com.duro.kukie.gitops.application.port.out.InstallStateCodec
import com.duro.kukie.gitops.domain.GitHubInstallationRepository
import com.duro.kukie.gitops.domain.InstallState
import com.duro.kukie.gitops.domain.InstallStateRepository
import com.duro.kukie.gitops.exception.GitOpsErrorCode
import com.duro.kukie.support.FakeGitHubAppGateway
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.time.Instant
import java.util.UUID

class GitHubAppIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var fakeGitHubAppGateway: FakeGitHubAppGateway

    @Autowired
    private lateinit var gitHubInstallationRepository: GitHubInstallationRepository

    @Autowired
    private lateinit var installStateCodec: InstallStateCodec

    @Autowired
    private lateinit var installStateRepository: InstallStateRepository

    @Test
    fun `설치를 시작하면 서명한 1회용 state 를 실은 GitHub 설치 링크를 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when
        val response = mockMvc.post("/teams/$teamId/github-app/installations") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.expiresAt") { exists() }
        }.andReturn().response.contentAsString

        // then
        val installUrl = JsonPath.read<String>(response, "$.installUrl")
        installUrl shouldStartWith "https://github.com/apps/kukie-gitops/installations/new?state="
        val state = installStateCodec.decode(installUrl.substringAfter("state="))!!
        state.teamId shouldBe teamId
        state.userId shouldBe admin.user.id
    }

    @Test
    fun `팀 관리자가 아니면 설치를 시작할 수 없다`() {
        // given
        val member = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN, member.user.id to TeamRole.MEMBER)

        // when & then
        mockMvc.post("/teams/$teamId/github-app/installations") {
            authorization(member.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.INSUFFICIENT_TEAM_ROLE.code) }
        }
    }

    @Test
    fun `설치 콜백을 받으면 우리 앱의 설치인지 확인하고 팀과 묶은 뒤 앱으로 보낸다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "Acme/Manifests", "acme/apps")
        val state = startInstall(admin, teamId)

        // when
        val landing = setup(installationId = INSTALLATION_ID, action = "install", state = state)

        // then
        landing.path shouldBe "/github-app/setup"
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "installed", "teamId" to teamId.toString())
        val installation = gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)!!
        installation.teamId shouldBe teamId
        installation.installedBy shouldBe admin.user.id
        installation.accountLogin shouldBe "acme"
        installation.repositories shouldBe listOf("acme/manifests", "acme/apps")
        mockMvc.get("/teams/$teamId/github-app") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("INSTALLED") }
            jsonPath("$.requestedAt") { value(null) }
            jsonPath("$.installations[0].installationId") { value(INSTALLATION_ID) }
            jsonPath("$.installations[0].account") { value("acme") }
            jsonPath("$.installations[0].repositories[0]") { value("acme/manifests") }
        }
    }

    @Test
    fun `위조한 state 는 거부한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests")
        val state = startInstall(admin, teamId)
        val otherTeamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val forged = state.substringBeforeLast('.') + "." + startInstall(admin, otherTeamId).substringAfterLast('.')

        // when
        val landing = setup(installationId = INSTALLATION_ID, action = "install", state = forged)

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INVALID_INSTALL_STATE.code)
        gitHubInstallationRepository.count() shouldBe 0
    }

    @Test
    fun `만료된 state 는 거부한다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests")
        val expired = InstallState(teamId, admin.user.id, "expired-nonce", Instant.now().minusSeconds(1801), Instant.now().minusSeconds(1))
        installStateRepository.save(expired.nonce, teamId, Duration.ofMinutes(30))

        // when
        val landing = setup(installationId = INSTALLATION_ID, action = "install", state = installStateCodec.encode(expired))

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INVALID_INSTALL_STATE.code)
        gitHubInstallationRepository.count() shouldBe 0
    }

    @Test
    fun `한 번 쓴 state 는 다시 쓸 수 없다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests")
        fakeGitHubAppGateway.install(OTHER_INSTALLATION_ID, "acme/apps")
        val state = startInstall(admin, teamId)
        setup(installationId = INSTALLATION_ID, action = "install", state = state)

        // when
        val landing = setup(installationId = OTHER_INSTALLATION_ID, action = "install", state = state)

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INVALID_INSTALL_STATE.code)
        gitHubInstallationRepository.findByInstallationId(OTHER_INSTALLATION_ID).shouldBeNull()
    }

    @Test
    fun `다른 앱의 설치나 없는 설치는 묶지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val state = startInstall(admin, teamId)

        // when
        val landing = setup(installationId = INSTALLATION_ID, action = "install", state = state)

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INSTALLATION_NOT_FOUND.code)
        gitHubInstallationRepository.count() shouldBe 0
    }

    @Test
    fun `다른 팀에 묶인 설치는 가져갈 수 없다`() {
        // given
        val owner = loggedInUser()
        val ownerTeamId = savedTeam(owner.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests")
        setup(installationId = INSTALLATION_ID, action = "install", state = startInstall(owner, ownerTeamId))
        val intruder = loggedInUser(UserFixture.user(email = "intruder@example.com"))
        val intruderTeamId = savedTeam(intruder.user.id to TeamRole.ADMIN)

        // when
        val landing = setup(installationId = INSTALLATION_ID, action = "install", state = startInstall(intruder, intruderTeamId))

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INSTALLATION_TAKEN.code)
        gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)!!.teamId shouldBe ownerTeamId
    }

    @Test
    fun `조직 관리자 승인을 기다리면 대기 중으로 보여 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when
        val landing = setup(installationId = null, action = "request", state = startInstall(admin, teamId))

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "requested", "teamId" to teamId.toString())
        mockMvc.get("/teams/$teamId/github-app") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.requestedAt") { exists() }
            jsonPath("$.installations") { isEmpty() }
        }
    }

    @Test
    fun `설치가 없고 기다리는 요청도 없으면 없음으로 보여 준다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)

        // when & then
        mockMvc.get("/teams/$teamId/github-app") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NONE") }
        }
    }

    @Test
    fun `state 없이 온 설정 변경은 GitHub 를 부르지 않고 앱으로만 보낸다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests")
        setup(installationId = INSTALLATION_ID, action = "install", state = startInstall(admin, teamId))
        val callsBefore = fakeGitHubAppGateway.calls

        // when
        val landing = setup(installationId = INSTALLATION_ID, action = "update", state = null)

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "updated")
        fakeGitHubAppGateway.calls shouldBe callsBefore
    }

    @Test
    fun `state 가 없거나 틀리면 GitHub 를 부르지 않는다`() {
        // given
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests")

        // when
        val missing = setup(installationId = INSTALLATION_ID, action = "install", state = null)
        val broken = setup(installationId = INSTALLATION_ID, action = "install", state = "not-a-state")

        // then
        missing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INVALID_INSTALL_STATE.code)
        broken.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INVALID_INSTALL_STATE.code)
        fakeGitHubAppGateway.calls shouldBe 0
    }

    @Test
    fun `state 를 받기 전부터 있던 설치는 묶지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "victim/manifests", createdAt = Instant.now().minus(Duration.ofDays(3)))

        // when
        val landing = setup(installationId = INSTALLATION_ID, action = "install", state = startInstall(admin, teamId))

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INSTALLATION_NOT_VERIFIED.code)
        gitHubInstallationRepository.count() shouldBe 0
    }

    @Test
    fun `묶이지 않은 설치의 설정 변경은 state 가 있어도 묶지 않는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests")

        // when
        val landing = setup(installationId = INSTALLATION_ID, action = "update", state = startInstall(admin, teamId))

        // then
        landing.queryParams.toSingleValueMap() shouldBe mapOf("result" to "failed", "error" to GitOpsErrorCode.INSTALLATION_NOT_VERIFIED.code)
        gitHubInstallationRepository.count() shouldBe 0
    }

    @Test
    fun `팀 관리자가 다시 읽으면 묶인 설치의 레포 목록을 GitHub 에서 새로 읽는다`() {
        // given
        val admin = loggedInUser()
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests")
        setup(installationId = INSTALLATION_ID, action = "install", state = startInstall(admin, teamId))
        fakeGitHubAppGateway.install(INSTALLATION_ID, "acme/manifests", "acme/apps")

        // when & then
        mockMvc.post("/teams/$teamId/github-app/refresh") {
            authorization(admin.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("INSTALLED") }
            jsonPath("$.installations[0].repositories.length()") { value(2) }
        }
        gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)!!.repositories shouldBe
            listOf("acme/manifests", "acme/apps")
    }

    private fun startInstall(admin: LoggedInUser, teamId: UUID): String {
        val response = mockMvc.post("/teams/$teamId/github-app/installations") {
            authorization(admin.accessToken)
        }.andReturn().response.contentAsString

        return JsonPath.read<String>(response, "$.installUrl").substringAfter("state=")
    }

    private fun setup(installationId: Long?, action: String, state: String?) =
        mockMvc.get("/github-app/setup") {
            installationId?.let { param("installation_id", it.toString()) }
            param("setup_action", action)
            state?.let { param("state", it) }
        }.andExpect {
            status { isFound() }
        }.andReturn().response.getHeader(HttpHeaders.LOCATION)!!.let { location ->
            location shouldStartWith "http://localhost:5173/github-app/setup?"
            UriComponentsBuilder.fromUriString(location).build()
        }

    companion object {
        private const val INSTALLATION_ID = 7L
        private const val OTHER_INSTALLATION_ID = 8L
    }
}
