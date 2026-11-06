package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import com.duro.kukie.serving.domain.*
import com.duro.kukie.serving.infrastructure.ServingUsageMaintenance
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID

class ServingUsageIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var usage: ServingUsageRepository
    @Autowired private lateinit var issue: IssueServingApiKeyService
    @Autowired private lateinit var begin: BeginServingUsageService
    @Autowired private lateinit var finish: CompleteServingUsageService
    @Autowired private lateinit var mine: GetMyServingUsageService
    @Autowired private lateinit var admin: GetAdminServingUsageService
    @Autowired private lateinit var clean: ServingUsageMaintenance
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `완료 요청을 저장하고 재조회해도 키별 일별 토큰 합계가 유지된다`() {
        // given
        val s = setup("a")
        val id = begin(s.identity, "shared")
        finish(event(s, id))
        // when
        val result = mine(s.identity.accessId, s.user.user.id, null, null)
        // then
        result.totals.requests shouldBe 1
        result.totals.inputTokens shouldBe 3
        result.totals.outputTokens shouldBe 7
        result.byKey.single().id shouldBe s.identity.keyId.toString()
        result.daily.single().totals.requests shouldBe 1
        result.byUser shouldBe emptyList()
        usage.findById(id).get().outcome shouldBe "COMPLETED"
        mine(s.identity.accessId, s.user.user.id, null, null).totals shouldBe result.totals
    }

    @Test
    fun `동일 완료 이벤트 재전달은 중복 집계하지 않는다`() {
        // given
        val s = setup("a")
        val id = begin(s.identity, "shared")
        // when
        repeat(2) { finish(event(s, id)) }
        // then
        admin(s.identity.clusterId, s.admin.user.id, null, null, null).totals.requests shouldBe 1
        admin(s.identity.clusterId, s.admin.user.id, null, null, null).totals.inputTokens shouldBe 3
    }

    @Test
    fun `동일 사용자의 A B 사용량과 다른 사용자의 키를 격리한다`() {
        // given
        val a = setup("a")
        val b = setup("b", a.user)
        finish(event(a, begin(a.identity, "shared")))
        finish(event(b, begin(b.identity, "shared")))
        // when then
        mine(a.identity.accessId, a.user.user.id, null, null).totals.requests shouldBe 1
        mockMvc.get("/serving/accesses/${a.identity.accessId}/usage") { authorization(b.admin.accessToken) }.andExpect { status { isNotFound() } }
        mockMvc.get("/clusters/${b.identity.clusterId}/serving/usage") { authorization(a.admin.accessToken) }.andExpect { status { isNotFound() } }
        mockMvc.get("/serving/accesses/${a.identity.accessId}/info") { authorization(a.user.accessToken) }.andExpect {
            status { isOk() }; jsonPath("$.baseUrl") { doesNotExist() }; jsonPath("$.serviceToken") { doesNotExist() }; jsonPath("$.limits.tokenBudgetSupported") { value(false) }
        }
    }

    @Test
    fun `시작 기록이 남은 재시작 요청은 완료로 추정하지 않는다`() {
        // given
        val s = setup("a")
        val id = begin(s.identity, "shared")
        // when then
        mine(s.identity.accessId, s.user.user.id, null, null).totals.inProgress shouldBe 1
        jdbc.update("update tbl_serving_usage set deadline_at = ? where id = ?", utcNow().minusSeconds(1), id)
        val beforeClean = mine(s.identity.accessId, s.user.user.id, null, null)
        beforeClean.totals.failed shouldBe 1
        beforeClean.totals.missingUsage shouldBe 1
        clean.clean()
        usage.findById(id).get().outcome shouldBe "INTERRUPTED"
        mine(s.identity.accessId, s.user.user.id, null, null).totals.inProgress shouldBe 0
    }

    @Test
    fun `스트림의 HTTP 200 이후 실패도 오류와 집계 누락으로 센다`() {
        // given
        val s = setup("a")
        val id = begin(s.identity, "shared")
        // when
        finish(event(s, id).copy(httpStatus = 504, wireStatus = 200, outcome = "timeout", inputTokens = null, outputTokens = null, usageMissing = true))
        // then
        val row = usage.findById(id).get()
        row.httpStatus shouldBe 504
        row.wireStatus shouldBe 200
        mine(s.identity.accessId, s.user.user.id, null, null).totals.failed shouldBe 1
        mine(s.identity.accessId, s.user.user.id, null, null).totals.missingUsage shouldBe 1
    }

    @Test
    fun `UTC 날짜 경계와 보관 기간을 적용하고 잘못된 기간을 거절한다`() {
        // given
        val s = setup("a")
        val yesterday = utcNow().toLocalDate().minusDays(1)
        val id = begin(s.identity, "shared")
        finish(event(s, id))
        jdbc.update("update tbl_serving_usage set started_at = ? where id = ?", yesterday.atStartOfDay(), id)
        // when then
        mine(s.identity.accessId, s.user.user.id, yesterday, yesterday).totals.requests shouldBe 1
        mine(s.identity.accessId, s.user.user.id, yesterday.plusDays(1), yesterday.plusDays(1)).totals.requests shouldBe 0
        mockMvc.get("/serving/accesses/${s.identity.accessId}/usage?from=${yesterday.minusDays(100)}") { authorization(s.user.accessToken) }.andExpect { status { isBadRequest() } }
        mockMvc.get("/serving/accesses/${s.identity.accessId}/usage?from=bad") { authorization(s.user.accessToken) }.andExpect { status { isBadRequest() } }
        jdbc.update("update tbl_serving_usage set started_at = ? where id = ?", yesterday.minusDays(100).atStartOfDay(), id)
        clean.clean()
        usage.existsById(id) shouldBe false
    }

    @Test
    fun `다른 키 식별자로 완료를 위조해도 시작 기록을 바꾸지 않는다`() {
        // given
        val a = setup("a")
        val b = setup("b")
        val id = begin(a.identity, "shared")
        // when
        finish(event(b, id))
        // then
        usage.findById(id).get().outcome shouldBe "IN_PROGRESS"
    }

    private fun event(s: Setup, id: UUID) = ServingUsageObservation(s.identity, "shared", Instant.now(), 100, 200, "completed", 3, 7, false, id, 200)
    private fun setup(label: String, client: LoggedInUser? = null): Setup {
        val admin = loggedInUser(UserFixture.user(email = "$label-admin@example.com"))
        val user = client ?: loggedInUser(UserFixture.user(email = "$label-user@example.com"))
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team, name = label))
        val access = accesses.save(ServingAccess(cluster.id, user.user.email, admin.user.id, utcNow().plusDays(7)).also { it.accept(user.user.id, user.user.email, utcNow()) })
        val key = issue(IssueServingApiKeyCommand(access.id, user.user.id, "test", 90))
        return Setup(admin, user, AuthenticatedServingKey(key.key.id, access.id, cluster.id, user.user.id))
    }
    private class Setup(val admin: LoggedInUser, val user: LoggedInUser, val identity: AuthenticatedServingKey)
}
