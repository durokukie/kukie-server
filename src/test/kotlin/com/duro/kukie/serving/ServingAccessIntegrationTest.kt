package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.domain.Email
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.`in`.InviteServingUserCommand
import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.TeamFixture
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ServingAccessIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var memberships: TeamMembershipRepository
    @Autowired private lateinit var permission: ServingAccessPermission
    @Autowired private lateinit var inviteService: InviteServingUserService
    @Autowired private lateinit var acceptService: AcceptServingInvitationService
    @Autowired private lateinit var revokeService: RevokeServingAccessService

    @Test
    fun `팀 가입 없이 A와 B의 API 이용 권한을 따로 수락한다`() {
        // given
        val a = setup("a")
        val b = setup("b")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val ia = invite(a, user.user.email.value)
        val ib = invite(b, user.user.email.value)

        // when
        accept(ia.id, user)
        accept(ib.id, user)

        // then
        mockMvc.get("/serving/clusters") { authorization(user.accessToken) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].apiServer") { doesNotExist() }
            jsonPath("$[0].credential") { doesNotExist() }
            jsonPath("$[0].namespace") { doesNotExist() }
        }
        memberships.existsByTeamIdAndUserId(a.cluster.teamId, user.user.id) shouldBe false
        memberships.existsByTeamIdAndUserId(b.cluster.teamId, user.user.id) shouldBe false
        mockMvc.get("/clusters/${a.cluster.id}") { authorization(user.accessToken) }.andExpect { status { isNotFound() } }
        permission.requireActive(ia.id, user.user.id).clusterId shouldBe a.cluster.id
        permission.requireActive(ib.id, user.user.id).clusterId shouldBe b.cluster.id
    }

    @Test
    fun `미가입 이메일도 초대하고 가입 후 본인만 수락한다`() {
        // given
        val owner = setup("owner")
        val invitation = invite(owner, "new@example.com")
        val user = loggedInUser(UserFixture.user(email = "new@example.com"))

        // when
        mockMvc.get("/serving/invitations") { authorization(user.accessToken) }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(invitation.id.toString()) }
            jsonPath("$[0].providerName") { value("DURO") }
            jsonPath("$[0].apiServer") { doesNotExist() }
        }
        accept(invitation.id, user)

        // then
        accesses.findByIdOrNull(invitation.id)!!.userId shouldBe user.user.id
        mockMvc.get("/serving/invitations") { authorization(user.accessToken) }.andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `초대는 이메일 대소문자를 정규화한다`() {
        // given
        val owner = setup("owner")
        val invitation = invite(owner, "CLIENT@EXAMPLE.COM")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))

        // when
        accept(invitation.id, user)

        // then
        permission.requireActive(invitation.id, user.user.id).email.value shouldBe "client@example.com"
    }

    @Test
    fun `일반 팀원은 키 사용자 관리자가 되지 않는다`() {
        // given
        val owner = setup("owner")
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        memberships.save(TeamFixture.membership(owner.cluster.teamId, member.user.id, TeamRole.MEMBER))

        // when then
        mockMvc.post("/clusters/${owner.cluster.id}/serving/invitations") {
            authorization(member.accessToken); contentType = MediaType.APPLICATION_JSON
            content = mapOf("email" to "other@example.com").toJson()
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/clusters/${owner.cluster.id}/serving/accesses") { authorization(member.accessToken) }
            .andExpect { status { isForbidden() } }
        mockMvc.get("/serving/clusters") { authorization(member.accessToken) }
            .andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `다른 회사 관리자는 목록과 초대에 접근할 수 없다`() {
        // given
        val a = setup("a")
        val b = setup("b")
        invite(b, "client@example.com")

        // when then
        mockMvc.get("/clusters/${b.cluster.id}/serving/accesses") { authorization(a.admin.accessToken) }
            .andExpect { status { isNotFound() } }
        mockMvc.post("/clusters/${b.cluster.id}/serving/invitations") {
            authorization(a.admin.accessToken); contentType = MediaType.APPLICATION_JSON
            content = mapOf("email" to "client2@example.com").toJson()
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `다른 사용자는 초대 조회 수락 거절을 할 수 없다`() {
        // given
        val owner = setup("owner")
        val invitation = invite(owner, "client@example.com")
        val stranger = loggedInUser(UserFixture.user(email = "stranger@example.com"))

        // when then
        mockMvc.get("/serving/invitations") { authorization(stranger.accessToken) }
            .andExpect { jsonPath("$.length()") { value(0) } }
        for (action in listOf("accept", "decline")) {
            mockMvc.post("/serving/invitations/${invitation.id}/$action") { authorization(stranger.accessToken) }
                .andExpect { status { isNotFound() } }
        }
        accesses.findByIdOrNull(invitation.id)!!.status shouldBe ServingAccessStatus.PENDING
    }

    @Test
    fun `만료 초대는 조회되지 않고 수락하면 410이다`() {
        // given
        val owner = setup("owner")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val expired = accesses.save(ServingAccess(owner.cluster.id, user.user.email, owner.admin.user.id, utcNow().minusDays(1)))

        // when then
        mockMvc.get("/serving/invitations") { authorization(user.accessToken) }
            .andExpect { jsonPath("$.length()") { value(0) } }
        mockMvc.post("/serving/invitations/${expired.id}/accept") { authorization(user.accessToken) }
            .andExpect { status { isGone() } }
        mockMvc.get("/clusters/${owner.cluster.id}/serving/accesses") { authorization(owner.admin.accessToken) }
            .andExpect { jsonPath("$.content[0].status") { value("EXPIRED") } }
    }

    @Test
    fun `유효한 초대는 중복 거절하고 만료된 초대는 새 ID로 재초대한다`() {
        // given
        val owner = setup("owner")
        val expired = accesses.save(ServingAccess(owner.cluster.id, Email("client@example.com"), owner.admin.user.id, utcNow().minusDays(1)))

        // when
        val fresh = invite(owner, "client@example.com")

        // then
        fresh.id shouldNotBe expired.id
        accesses.findByIdOrNull(expired.id)!!.status shouldBe ServingAccessStatus.EXPIRED
        mockMvc.post("/clusters/${owner.cluster.id}/serving/invitations") {
            authorization(owner.admin.accessToken); contentType = MediaType.APPLICATION_JSON
            content = mapOf("email" to "client@example.com").toJson()
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `A 권한을 취소해도 B는 유지되고 재초대는 옛 권한을 살리지 않는다`() {
        // given
        val a = setup("a")
        val b = setup("b")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val ia = invite(a, user.user.email.value)
        val ib = invite(b, user.user.email.value)
        accept(ia.id, user); accept(ib.id, user)

        // when
        mockMvc.delete("/clusters/${a.cluster.id}/serving/accesses/${ia.id}") { authorization(a.admin.accessToken) }
            .andExpect { status { isNoContent() } }
        val newInvitation = invite(a, user.user.email.value)
        accept(newInvitation.id, user)

        // then
        shouldThrow<ServingAccessException> { permission.requireActive(ia.id, user.user.id) }
        permission.requireActive(ib.id, user.user.id).status shouldBe ServingAccessStatus.ACTIVE
        permission.requireActive(newInvitation.id, user.user.id).status shouldBe ServingAccessStatus.ACTIVE
        newInvitation.id shouldNotBe ia.id
    }

    @Test
    fun `A 경로에 B 권한 ID를 넣어도 취소되지 않는다`() {
        // given
        val a = setup("a"); val b = setup("b")
        val invitation = invite(b, "client@example.com")

        // when then
        mockMvc.delete("/clusters/${a.cluster.id}/serving/accesses/${invitation.id}") { authorization(a.admin.accessToken) }
            .andExpect { status { isNotFound() } }
        accesses.findByIdOrNull(invitation.id)!!.status shouldBe ServingAccessStatus.PENDING
    }

    @Test
    fun `API 이용자는 관리자 목록을 읽거나 권한을 취소하지 못한다`() {
        // given
        val owner = setup("owner")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val invitation = invite(owner, user.user.email.value)
        accept(invitation.id, user)

        // when then
        mockMvc.get("/clusters/${owner.cluster.id}/serving/accesses") { authorization(user.accessToken) }
            .andExpect { status { isNotFound() } }
        mockMvc.delete("/clusters/${owner.cluster.id}/serving/accesses/${invitation.id}") { authorization(user.accessToken) }
            .andExpect { status { isNotFound() } }
        shouldThrow<ServingAccessException> { permission.requireActive(invitation.id, owner.admin.user.id) }
    }

    @Test
    fun `관리 권한을 잃으면 기존 키 관리 경로도 막힌다`() {
        // given
        val owner = setup("owner")
        val membership = memberships.findAllByTeamId(owner.cluster.teamId).single()
        membership.changeRole(TeamRole.MEMBER)
        memberships.saveAndFlush(membership)

        // when then
        mockMvc.get("/clusters/${owner.cluster.id}/serving/accesses") { authorization(owner.admin.accessToken) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `초대 거절 후에는 내 API 목록에 나오지 않고 새 초대를 받을 수 있다`() {
        // given
        val owner = setup("owner")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val invitation = invite(owner, user.user.email.value)

        // when
        mockMvc.post("/serving/invitations/${invitation.id}/decline") { authorization(user.accessToken) }
            .andExpect { status { isNoContent() } }

        // then
        mockMvc.get("/serving/clusters") { authorization(user.accessToken) }.andExpect { jsonPath("$.length()") { value(0) } }
        invite(owner, user.user.email.value).id shouldNotBe invitation.id
    }

    @Test
    fun `클러스터가 삭제되면 이용 권한도 제거된다`() {
        // given
        val owner = setup("owner")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val invitation = invite(owner, user.user.email.value)
        accept(invitation.id, user)

        // when
        clusters.deleteById(owner.cluster.id)

        // then
        accesses.existsById(invitation.id) shouldBe false
        shouldThrow<ServingAccessException> { permission.requireActive(invitation.id, user.user.id) }
    }

    @Test
    fun `인증 없는 요청과 잘못된 이메일 및 페이지 범위를 거절한다`() {
        // given
        val owner = setup("owner")

        // when then
        mockMvc.get("/serving/clusters").andExpect { status { isUnauthorized() } }
        mockMvc.post("/clusters/${owner.cluster.id}/serving/invitations") {
            authorization(owner.admin.accessToken); contentType = MediaType.APPLICATION_JSON
            content = mapOf("email" to "not-an-email").toJson()
        }.andExpect { status { isBadRequest() } }
        mockMvc.get("/clusters/${owner.cluster.id}/serving/accesses?size=101") { authorization(owner.admin.accessToken) }
            .andExpect { status { isBadRequest() } }
        mockMvc.get("/clusters/${owner.cluster.id}/serving/accesses?page=-1") { authorization(owner.admin.accessToken) }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `동시에 같은 이메일을 초대해도 한 건만 만들어진다`() {
        // given
        val owner = setup("owner")
        val barrier = CyclicBarrier(2)

        // when
        val results = Executors.newFixedThreadPool(2).use { pool ->
            pool.invokeAll((1..2).map {
                Callable {
                    barrier.await(5, TimeUnit.SECONDS)
                    runCatching { inviteService(InviteServingUserCommand(owner.cluster.id, owner.admin.user.id, Email("client@example.com"))) }
                }
            }).map { it.get(10, TimeUnit.SECONDS) }
        }

        // then
        results.count { it.isSuccess } shouldBe 1
        results.count { it.exceptionOrNull() is ServingAccessException } shouldBe 1
        accesses.count() shouldBe 1
    }

    @Test
    fun `동시 수락과 취소가 끝나면 취소된 권한은 활성으로 돌아오지 않는다`() {
        // given
        val owner = setup("owner")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val invitation = invite(owner, user.user.email.value)
        val barrier = CyclicBarrier(2)

        // when
        Executors.newFixedThreadPool(2).use { pool ->
            val results = pool.invokeAll(listOf(
                Callable { barrier.await(5, TimeUnit.SECONDS); runCatching { acceptService(invitation.id, user.user.id) } },
                Callable { barrier.await(5, TimeUnit.SECONDS); runCatching { revokeService(owner.cluster.id, invitation.id, owner.admin.user.id) } },
            )).map { it.get(10, TimeUnit.SECONDS) }
            results[1].isSuccess shouldBe true
            results[0].exceptionOrNull()?.let { (it is ServingAccessException) shouldBe true }
        }

        // then
        accesses.findByIdOrNull(invitation.id)!!.status shouldBe ServingAccessStatus.REVOKED
        shouldThrow<ServingAccessException> { permission.requireActive(invitation.id, user.user.id) }
    }

    @Test
    fun `활성 이용 권한이 있으면 다시 초대할 수 없다`() {
        // given
        val owner = setup("owner")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val invitation = invite(owner, user.user.email.value)
        accept(invitation.id, user)

        // when then
        mockMvc.post("/clusters/${owner.cluster.id}/serving/invitations") {
            authorization(owner.admin.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = mapOf("email" to user.user.email.value).toJson()
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `관리자가 대기 초대를 취소하면 더 이상 수락할 수 없다`() {
        // given
        val owner = setup("owner")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val invitation = invite(owner, user.user.email.value)

        // when
        mockMvc.delete("/clusters/${owner.cluster.id}/serving/accesses/${invitation.id}") {
            authorization(owner.admin.accessToken)
        }.andExpect { status { isNoContent() } }

        // then
        mockMvc.post("/serving/invitations/${invitation.id}/accept") { authorization(user.accessToken) }
            .andExpect { status { isConflict() } }
        mockMvc.get("/serving/invitations") { authorization(user.accessToken) }
            .andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `팀 삭제 시 API 이용 권한도 지워지고 다른 회사 권한은 유지된다`() {
        // given
        val a = setup("a")
        val b = setup("b")
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        val ia = invite(a, user.user.email.value)
        val ib = invite(b, user.user.email.value)
        accept(ia.id, user)
        accept(ib.id, user)

        // when
        mockMvc.delete("/teams/${a.cluster.teamId}") { authorization(a.admin.accessToken) }
            .andExpect { status { isNoContent() } }

        // then
        accesses.existsById(ia.id) shouldBe false
        permission.requireActive(ib.id, user.user.id).status shouldBe ServingAccessStatus.ACTIVE
    }

    private fun setup(label: String): Setup {
        val admin = loggedInUser(UserFixture.user(email = "$label@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, teamId, name = label))
        return Setup(admin, cluster)
    }

    private fun invite(setup: Setup, email: String): ServingAccess {
        mockMvc.post("/clusters/${setup.cluster.id}/serving/invitations") {
            authorization(setup.admin.accessToken); contentType = MediaType.APPLICATION_JSON
            content = mapOf("email" to email).toJson()
        }.andExpect { status { isCreated() } }
        return accesses.findAll().single {
            it.clusterId == setup.cluster.id && it.email == Email(email) && it.status == ServingAccessStatus.PENDING
        }
    }

    private fun accept(id: UUID, user: LoggedInUser) {
        mockMvc.post("/serving/invitations/$id/accept") { authorization(user.accessToken) }
            .andExpect { status { isNoContent() } }
    }

    private data class Setup(val admin: LoggedInUser, val cluster: Cluster)
}
