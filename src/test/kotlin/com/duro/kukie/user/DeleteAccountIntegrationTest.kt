package com.duro.kukie.user

import com.duro.kukie.chat.ChatFixture
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.config.properties.AuthCookieProperties
import com.duro.kukie.global.domain.Email
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.domain.HostingIdentity
import com.duro.kukie.notification.domain.Notification
import com.duro.kukie.notification.domain.NotificationRepository
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.support.FakeHostingCommandQueue
import com.duro.kukie.support.FakeHostingStateStore
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.exception.UserErrorCode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class DeleteAccountIntegrationTest : IntegrationTest() {

    @Autowired private lateinit var cookies: AuthCookieProperties
    @Autowired private lateinit var teams: TeamRepository
    @Autowired private lateinit var memberships: TeamMembershipRepository
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var sessions: ChatSessionRepository
    @Autowired private lateinit var notifications: NotificationRepository
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var hostingStore: FakeHostingStateStore
    @Autowired private lateinit var hostingQueue: FakeHostingCommandQueue

    @Test
    fun `탈퇴하면 개인정보를 지우고 로그인을 모두 끊는다`() {
        val me = loggedInUser()
        val otherLogin = loggedInUser(me.user)

        deleteMe(me.accessToken).andExpect {
            status { isNoContent() }
            cookie {
                maxAge(cookies.accessCookieName, 0)
                maxAge(cookies.refreshCookieName, 0)
            }
        }

        val user = userRepository.findByIdOrNull(me.user.id)!!
        user.name shouldBe User.DELETED_NAME
        user.email shouldNotBe Email(UserFixture.DEFAULT_EMAIL)
        user.hasPassword shouldBe false
        user.githubId shouldBe null
        user.deletedAt shouldNotBe null
        userRepository.existsByEmail(Email(UserFixture.DEFAULT_EMAIL)) shouldBe false

        mockMvc.get("/users/me") { authorization(otherLogin.accessToken) }.andExpect { status { isUnauthorized() } }
        refresh(me.refreshToken).andExpect { status { isUnauthorized() } }
        refresh(otherLogin.refreshToken).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `다른 팀원이 있는 팀의 유일한 관리자면 탈퇴할 수 없고 아무것도 바뀌지 않는다`() {
        val me = loggedInUser()
        val member = userRepository.save(UserFixture.user(email = "member@example.com"))
        val teamId = savedTeam(me.user.id to TeamRole.ADMIN, member.id to TeamRole.MEMBER)

        deleteMe(me.accessToken).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(UserErrorCode.LAST_ADMIN_OF_TEAM.code) }
        }

        userRepository.findByIdOrNull(me.user.id)!!.deletedAt shouldBe null
        memberships.existsByTeamIdAndUserId(teamId, me.user.id) shouldBe true
        refresh(me.refreshToken).andExpect { status { isOk() } }
    }

    @Test
    fun `혼자인 팀은 지우고, 관리자가 더 있는 팀에서는 빠지기만 한다`() {
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val alone = savedTeam(me.user.id to TeamRole.ADMIN)
        clusters.save(ClusterFixture.cluster(registeredBy = me.user.id, teamId = alone))
        val shared = savedTeam(me.user.id to TeamRole.ADMIN, other.id to TeamRole.ADMIN)
        val joined = savedTeam(other.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER)

        deleteMe(me.accessToken).andExpect { status { isNoContent() } }

        teams.existsById(alone) shouldBe false
        clusters.findAll().none { it.teamId == alone } shouldBe true
        teams.existsById(shared) shouldBe true
        teams.existsById(joined) shouldBe true
        memberships.findAllByUserId(me.user.id) shouldBe emptyList()
    }

    @Test
    fun `내 개인 대화와 알림은 지우고 팀 공유 대화는 남긴다`() {
        val me = loggedInUser()
        val other = userRepository.save(UserFixture.user(email = "other@example.com"))
        val teamId = savedTeam(other.id to TeamRole.ADMIN, me.user.id to TeamRole.MEMBER)
        val cluster = clusters.save(ClusterFixture.cluster(registeredBy = other.id, teamId = teamId))
        val private = sessions.save(ChatFixture.session(me.user.id, cluster.id, ChatVisibility.PRIVATE))
        val shared = sessions.save(ChatFixture.session(me.user.id, cluster.id, ChatVisibility.SHARED))
        notifications.save(Notification.roleChanged(me.user.id, teamId, "팀", TeamRole.MEMBER))

        deleteMe(me.accessToken).andExpect { status { isNoContent() } }

        sessions.existsById(private.id) shouldBe false
        sessions.existsById(shared.id) shouldBe true
        notifications.findAllByUserIdOrderByCreatedAtDesc(me.user.id) shouldBe emptyList()
    }

    @Test
    fun `내 서빙 접근을 취소해서 API 키가 더는 통하지 않게 한다`() {
        val me = loggedInUser()
        val admin = userRepository.save(UserFixture.user(email = "admin@example.com"))
        val teamId = savedTeam(admin.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(registeredBy = admin.id, teamId = teamId))
        val access = accesses.save(
            ServingAccess(cluster.id, me.user.email, admin.id, utcNow().plusDays(7))
                .also { it.accept(me.user.id, me.user.email, utcNow()) },
        )

        deleteMe(me.accessToken).andExpect { status { isNoContent() } }

        accesses.findByIdOrNull(access.id)!!.status shouldBe ServingAccessStatus.REVOKED
    }

    @Test
    fun `탈퇴하면 내 호스팅 앱마다 삭제 명령을 보낸다`() {
        val me = loggedInUser(UserFixture.oAuthUser().apply { linkGitHub(GitHubAccount(GITHUB_ID, "Recklehs")) })
        hostingStore.files["apps/recklehs/shop.json"] = hostingAppJson("shop", GITHUB_ID)
        hostingStore.files["apps/recklehs/blog.json"] = hostingAppJson("blog", GITHUB_ID)
        hostingStore.files["apps/recklehs/stolen.json"] = hostingAppJson("stolen", ownerId = 999)

        deleteMe(me.accessToken).andExpect { status { isNoContent() } }

        hostingQueue.commands.map { it.type to it.app }.toSet() shouldBe
            setOf(HostingCommand.DELETE to "shop", HostingCommand.DELETE to "blog")
        hostingQueue.commands.map { it.generation }.toSet() shouldBe setOf(HOSTING_GENERATION)
    }

    @Test
    fun `GitHub 를 연결하지 않은 회원이 탈퇴하면 Kukie 계정의 앱마다 삭제 명령을 보낸다`() {
        val me = loggedInUser()
        val kukie = HostingIdentity.kukie(me.user.id)
        hostingStore.files["apps/${kukie.team}/notes.json"] = hostingAppJson("notes", kukie.id).replace("recklehs", kukie.team)

        deleteMe(me.accessToken).andExpect { status { isNoContent() } }

        with(hostingQueue.commands.single()) {
            type shouldBe HostingCommand.DELETE
            team shouldBe kukie.team
            app shouldBe "notes"
        }
    }

    @Test
    fun `호스팅 명령을 보내지 못해도 탈퇴는 된다`() {
        val me = loggedInUser(UserFixture.oAuthUser().apply { linkGitHub(GitHubAccount(GITHUB_ID, "Recklehs")) })
        hostingStore.files["apps/recklehs/shop.json"] = hostingAppJson("shop", GITHUB_ID)
        hostingQueue.shouldFail = true

        deleteMe(me.accessToken).andExpect { status { isNoContent() } }

        userRepository.findByIdOrNull(me.user.id)!!.deletedAt shouldNotBe null
    }

    @Test
    fun `로그인하지 않으면 탈퇴할 수 없다`() {
        mockMvc.delete("/users/me").andExpect { status { isUnauthorized() } }
    }

    private fun deleteMe(accessToken: String) =
        mockMvc.delete("/users/me") { authorization(accessToken) }

    private fun refresh(refreshToken: String) =
        mockMvc.post("/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = RefreshTokenRequest(refreshToken).toJson()
        }

    companion object {
        const val GITHUB_ID = 127021195L
        const val HOSTING_GENERATION = "20261101T031500Z"

        fun hostingAppJson(app: String, ownerId: Long) = """
            {
              "version": 1,
              "owner": { "id": $ownerId, "login": "Recklehs", "type": "User" },
              "repo": { "id": 987654321, "fullName": "Recklehs/$app" },
              "team": "recklehs",
              "app": "$app",
              "generation": "$HOSTING_GENERATION",
              "url": "https://$app-recklehs.apps.dev.durokukie.com",
              "config": { "port": 3000, "size": "S", "source": "default" },
              "latest": {
                "deploymentId": "20261110T011744Z-0123456",
                "phase": "succeeded",
                "commit": { "sha": "0123456789abcdef", "message": "feat: 첫 화면" },
                "at": "2026-11-10T01:19:36Z"
              }
            }
        """.trimIndent()
    }
}
