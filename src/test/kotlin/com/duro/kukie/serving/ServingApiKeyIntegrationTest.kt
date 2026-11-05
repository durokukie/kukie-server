package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.domain.Email
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingApiKey
import com.duro.kukie.serving.domain.ServingApiKeyRepository
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import com.duro.kukie.serving.infrastructure.ServingKeyTokens
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ServingApiKeyIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var keys: ServingApiKeyRepository
    @Autowired private lateinit var tokens: ServingKeyTokens
    @Autowired private lateinit var issue: IssueServingApiKeyService
    @Autowired private lateinit var authenticate: AuthenticateServingApiKeyService
    @Autowired private lateinit var revokeAccess: RevokeServingAccessService
    @Autowired private lateinit var revokeKey: RevokeMyServingApiKeyService
    @Autowired private lateinit var memberships: TeamMembershipRepository

    @Test
    fun `키 원문은 발급에만 반환하고 저장과 목록은 비밀을 노출하지 않는다`() {
        // given
        val s = setup()
        // when
        val response = mockMvc.post("/serving/accesses/${s.access.id}/keys") {
            authorization(s.user.accessToken); contentType = MediaType.APPLICATION_JSON
            content = mapOf("name" to "개발").toJson()
        }.andExpect {
            status { isCreated() }; header { string("Cache-Control", "no-store") }
            jsonPath("$.token") { isString() }; jsonPath("$.key.tokenHash") { doesNotExist() }
        }.andReturn().response.contentAsString
        // then
        val stored = keys.findAll().single()
        val raw = Regex("\"token\":\"([^\"]+)\"").find(response)!!.groupValues[1]
        tokens.isValidFormat(raw) shouldBe true
        stored.tokenHash shouldBe tokens.hash(raw)
        stored.tokenHash shouldNotBe raw
        stored.prefix shouldBe raw.take(12)
        authenticate(raw).clusterId shouldBe s.access.clusterId
        authenticate(raw).userId shouldBe s.user.user.id
        mockMvc.get("/serving/accesses/${s.access.id}/keys") { authorization(s.user.accessToken) }.andExpect {
            status { isOk() }; jsonPath("$.content[0].token") { doesNotExist() }; jsonPath("$.content[0].tokenHash") { doesNotExist() }
        }
        val issued = issue(command(s))
        issued.toString().contains(issued.token) shouldBe false
    }

    @Test
    fun `본인과 해당 관리자만 목록과 폐기에 접근한다`() {
        // given
        val s = setup()
        val other = setup("other")
        val key = issue(command(s))
        // when then
        mockMvc.get("/serving/accesses/${s.access.id}/keys") { authorization(other.user.accessToken) }.andExpect { status { isNotFound() } }
        mockMvc.delete("/serving/accesses/${s.access.id}/keys/${key.key.id}") { authorization(other.user.accessToken) }.andExpect { status { isNotFound() } }
        mockMvc.get("/clusters/${s.access.clusterId}/serving/accesses/${s.access.id}/keys") { authorization(s.admin.accessToken) }.andExpect { status { isOk() } }
        mockMvc.get("/clusters/${other.access.clusterId}/serving/accesses/${s.access.id}/keys") { authorization(other.admin.accessToken) }.andExpect { status { isNotFound() } }
        mockMvc.delete("/clusters/${other.access.clusterId}/serving/accesses/${s.access.id}/keys/${key.key.id}") { authorization(other.admin.accessToken) }.andExpect { status { isNotFound() } }
        mockMvc.delete("/clusters/${s.access.clusterId}/serving/accesses/${s.access.id}/keys/${key.key.id}") { authorization(s.admin.accessToken) }.andExpect { status { isNoContent() } }
        shouldThrow<ServingAccessException> { authenticate(key.token) }
    }

    @Test
    fun `관리자도 사용자 대신 키를 발급하지 못하고 일반 팀원은 관리하지 못한다`() {
        // given
        val s = setup()
        val member = loggedInUser(UserFixture.user(email = "member@example.com"))
        memberships.save(TeamFixture.membership(s.teamId, member.user.id, TeamRole.MEMBER))
        // when then
        mockMvc.post("/serving/accesses/${s.access.id}/keys") {
            authorization(s.admin.accessToken); contentType = MediaType.APPLICATION_JSON; content = mapOf("name" to "개발").toJson()
        }.andExpect { status { isNotFound() } }
        mockMvc.get("/clusters/${s.access.clusterId}/serving/accesses/${s.access.id}/keys") { authorization(member.accessToken) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `키 폐기는 멱등이고 같은 사용자의 다른 키는 유지된다`() {
        // given
        val s = setup()
        val old = issue(command(s))
        val replacement = issue(command(s))
        // when
        repeat(2) { revokeKey(s.access.id, s.user.user.id, old.key.id) }
        // then
        shouldThrow<ServingAccessException> { authenticate(old.token) }
        authenticate(replacement.token).keyId shouldBe replacement.key.id
        keys.findById(old.key.id).get().revokedAt shouldNotBe null
    }

    @Test
    fun `권한 취소와 재초대는 과거 키를 되살리지 않고 다른 회사 키는 유지한다`() {
        // given
        val a = setup()
        val b = setup("b", a.user)
        val old = issue(command(a))
        val other = issue(command(b))
        // when
        revokeAccess(a.access.clusterId, a.access.id, a.admin.user.id)
        val fresh = accesses.save(ServingAccess(a.access.clusterId, a.user.user.email, a.admin.user.id, utcNow().plusDays(7)).also {
            it.accept(a.user.user.id, a.user.user.email, utcNow())
        })
        // then
        shouldThrow<ServingAccessException> { authenticate(old.token) }
        shouldThrow<ServingAccessException> { issue(command(a)) }
        authenticate(other.token).clusterId shouldBe b.access.clusterId
        val newKey = issue(IssueServingApiKeyCommand(fresh.id, a.user.user.id, "재초대", 90))
        authenticate(newKey.token).accessId shouldBe fresh.id
        revokeKey(a.access.id, a.user.user.id, old.key.id)
        shouldThrow<ServingAccessException> { authenticate(old.token) }
    }

    @Test
    fun `만료된 키와 잘못된 키는 같은 인증 오류로 거절한다`() {
        // given
        val s = setup()
        val expired = tokens.generate()
        keys.save(ServingApiKey(s.access.id, "만료", tokens.hash(expired), expired.take(12), utcNow().minusSeconds(1)))
        // when then
        for (raw in listOf(expired, tokens.generate(), "", "Bearer invalid", "kk_" + "a".repeat(1000))) {
            val error = shouldThrow<ServingAccessException> { authenticate(raw) }
            error.errorCode shouldBe ServingErrorCode.SERVING_KEY_INVALID
            error.message.contains(raw).takeIf { raw.isNotEmpty() } shouldNotBe true
        }
        val issued = issue(command(s))
        authenticate(issued.token).accessId shouldBe s.access.id
    }

    @Test
    fun `개수 상한은 만료와 폐기 키를 제외하며 입력과 페이지를 검증한다`() {
        // given
        val s = setup()
        val issued = (1..5).map { issue(command(s)) }
        // when then
        shouldThrow<ServingAccessException> { issue(command(s)) }
        revokeKey(s.access.id, s.user.user.id, issued.first().key.id)
        issue(command(s))
        for (days in listOf(0, 366)) {
            mockMvc.post("/serving/accesses/${s.access.id}/keys") {
                authorization(s.user.accessToken); contentType = MediaType.APPLICATION_JSON
                content = mapOf("name" to "개발", "expiresInDays" to days).toJson()
            }.andExpect { status { isBadRequest() } }
        }
        mockMvc.post("/serving/accesses/${s.access.id}/keys") {
            authorization(s.user.accessToken); contentType = MediaType.APPLICATION_JSON; content = mapOf("name" to " ").toJson()
        }.andExpect { status { isBadRequest() } }
        mockMvc.get("/serving/accesses/${s.access.id}/keys?size=101") { authorization(s.user.accessToken) }.andExpect { status { isBadRequest() } }
        mockMvc.get("/serving/accesses/${s.access.id}/keys").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `동시 발급도 유효 키 다섯 개 상한을 넘지 못한다`() {
        // given
        val s = setup()
        repeat(4) { issue(command(s)) }
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        // when
        try {
            val results = executor.invokeAll((1..2).map { Callable {
                barrier.await(10, TimeUnit.SECONDS)
                try { issue(command(s)); true } catch (_: ServingAccessException) { false }
            } }).map { it.get(20, TimeUnit.SECONDS) }
            // then
            results.count { it } shouldBe 1
            keys.countByAccessIdAndRevokedAtIsNullAndExpiresAtAfter(s.access.id, utcNow()) shouldBe 5
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `클러스터 삭제는 키도 삭제하고 인증을 차단한다`() {
        // given
        val s = setup()
        val issued = issue(command(s))
        // when
        mockMvc.delete("/clusters/${s.access.clusterId}") { authorization(s.admin.accessToken) }.andExpect { status { isNoContent() } }
        // then
        keys.existsById(issued.key.id) shouldBe false
        shouldThrow<ServingAccessException> { authenticate(issued.token) }
    }

    @Test
    fun `같은 사용자의 다른 회사 키도 경로를 바꿔 폐기할 수 없다`() {
        // given
        val a = setup()
        val b = setup("b", a.user)
        val key = issue(command(b))
        // when then
        mockMvc.delete("/serving/accesses/${a.access.id}/keys/${key.key.id}") { authorization(a.user.accessToken) }
            .andExpect { status { isNotFound() } }
        authenticate(key.token).clusterId shouldBe b.access.clusterId
    }

    @Test
    fun `초대 수락 전에는 키를 발급할 수 없고 만료 키는 상한에서 제외한다`() {
        // given
        val s = setup()
        val pending = accesses.save(ServingAccess(s.access.clusterId, Email("pending@example.com"), s.admin.user.id, utcNow().plusDays(7)))
        repeat(5) {
            val raw = tokens.generate()
            keys.save(ServingApiKey(s.access.id, "만료", tokens.hash(raw), raw.take(12), utcNow().minusSeconds(1)))
        }
        // when then
        shouldThrow<ServingAccessException> { issue(IssueServingApiKeyCommand(pending.id, s.user.user.id, "개발", 90)) }
        val fresh = issue(command(s))
        authenticate(fresh.token).accessId shouldBe s.access.id
    }

    @Test
    fun `발급과 권한 취소가 경합해도 취소 후 사용할 수 있는 키는 없다`() {
        // given
        val s = setup()
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        // when
        try {
            val issued = executor.submit(Callable {
                barrier.await(10, TimeUnit.SECONDS)
                try { issue(command(s)) } catch (_: ServingAccessException) { null }
            })
            val revoked = executor.submit(Callable {
                barrier.await(10, TimeUnit.SECONDS)
                revokeAccess(s.access.clusterId, s.access.id, s.admin.user.id)
            })
            val result = issued.get(20, TimeUnit.SECONDS)
            revoked.get(20, TimeUnit.SECONDS)
            // then
            result?.let { shouldThrow<ServingAccessException> { authenticate(it.token) } }
            shouldThrow<ServingAccessException> { issue(command(s)) }
        } finally { executor.shutdownNow() }
    }

    private fun command(s: Setup) = IssueServingApiKeyCommand(s.access.id, s.user.user.id, "개발", 90)

    private fun setup(label: String = "a", client: LoggedInUser? = null): Setup {
        val admin = loggedInUser(UserFixture.user(email = "$label-admin@example.com"))
        val user = client ?: loggedInUser(UserFixture.user(email = "$label-user@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, teamId, name = label))
        val access = accesses.save(ServingAccess(cluster.id, Email(user.user.email.value), admin.user.id, utcNow().plusDays(7)).also {
            it.accept(user.user.id, user.user.email, utcNow())
        })
        return Setup(admin, user, teamId, access)
    }

    private data class Setup(val admin: LoggedInUser, val user: LoggedInUser, val teamId: UUID, val access: ServingAccess)
}
