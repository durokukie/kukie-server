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
import com.duro.kukie.serving.presentation.dto.response.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
    @Autowired private lateinit var overview: GetAdminServingUsageOverviewService
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

    @Test
    fun `관리자 요약은 최근 24시간의 합계 분위수 시간별 거절 최근 기록을 준다`() {
        // given
        val s = setup("a")
        val hour = utcNow().truncatedTo(ChronoUnit.HOURS)
        row(s, "shared", hour, "COMPLETED", 100, 10, 3, 7)
        row(s, "shared", hour.minusHours(2), "FAILED", 300, 30)
        row(s, "other", hour, "COMPLETED", 200, null, 1, 2)
        row(s, "shared", hour.minusHours(1), "IN_PROGRESS", null, null, deadline = utcNow().minusSeconds(1))
        row(s, "shared", hour.minusHours(24), "COMPLETED", 50, 5)
        rejection(s, "shared", "concurrency_limit", hour.plusMinutes(1), 4)
        rejection(s, "other", "model_not_allowed", hour.minusHours(2), 1)
        rejection(s, null, "connector_busy", hour.minusHours(30), 2)
        // when
        val all = overview(s.identity.clusterId, s.admin.user.id, null)
        val shared = overview(s.identity.clusterId, s.admin.user.id, "shared")
        // then
        all.from shouldBe hour.minusHours(23); all.model shouldBe null; all.models shouldBe listOf("other", "shared")
        all.totals shouldBe ServingUsageOverviewTotals(4, 2, 2, 0, 5, 200, 290, 20, 29, 4, 9)
        all.rejectedByReason shouldBe listOf(ServingRejectionCount("concurrency_limit", 4), ServingRejectionCount("model_not_allowed", 1))
        all.hourly.size shouldBe 24
        all.hourly.first() shouldBe ServingUsageHour(hour.minusHours(23), 0, 0, 0, null)
        all.hourly[21] shouldBe ServingUsageHour(hour.minusHours(2), 1, 1, 1, 30)
        all.hourly[22] shouldBe ServingUsageHour(hour.minusHours(1), 1, 1, 0, null)
        all.hourly[23] shouldBe ServingUsageHour(hour, 2, 0, 4, 10)
        all.recent.map { it.outcome } shouldBe listOf("COMPLETED", "COMPLETED", "INTERRUPTED", "FAILED")
        all.recent.last().let { it.status shouldBe 500; it.durationMs shouldBe 300; it.ttfbMs shouldBe 30; it.userEmail shouldBe s.user.user.email.value; it.inputTokens shouldBe null }
        shared.model shouldBe "shared"; shared.models shouldBe listOf("other", "shared")
        shared.totals.requests shouldBe 3; shared.totals.rejected shouldBe 4; shared.recent.size shouldBe 3
        shared.rejectedByReason shouldBe listOf(ServingRejectionCount("concurrency_limit", 4))
        // 기존 사용량 조회는 거절을 요청으로 세지 않는다
        admin(s.identity.clusterId, s.admin.user.id, null, hour.toLocalDate().minusDays(1), null).totals.requests shouldBe 5
        mockMvc.get("/clusters/${s.identity.clusterId}/serving/usage/overview?model=other") { authorization(s.admin.accessToken) }.andExpect {
            status { isOk() }
            jsonPath("$.model") { value("other") }; jsonPath("$.totals.requests") { value(1) }; jsonPath("$.totals.rejected") { value(1) }
            jsonPath("$.totals.ttfbP50Ms") { value(nullValue()) }; jsonPath("$.hourly.length()") { value(24) }; jsonPath("$.recent[0].userEmail") { value(s.user.user.email.value) }
        }
    }

    @Test
    fun `기록이 없으면 분위수는 비우고 시간별은 0으로 채우며 관리자만 볼 수 있다`() {
        // given
        val a = setup("a")
        val b = setup("b")
        // when
        val result = overview(a.identity.clusterId, a.admin.user.id, null)
        // then
        result.totals shouldBe ServingUsageOverviewTotals(0, 0, 0, 0, 0, null, null, null, null, 0, 0)
        result.hourly.size shouldBe 24; result.hourly.all { it.requests == 0L && it.rejected == 0L } shouldBe true
        result.recent shouldBe emptyList(); result.models shouldBe emptyList()
        for (token in listOf(a.user.accessToken, b.admin.accessToken)) {
            val expected = mockMvc.get("/clusters/${a.identity.clusterId}/serving/usage") { authorization(token) }.andReturn().response.status
            expected shouldNotBe 200
            mockMvc.get("/clusters/${a.identity.clusterId}/serving/usage/overview") { authorization(token) }.andExpect { status { isEqualTo(expected) } }
        }
    }

    private fun row(s: Setup, model: String?, startedAt: LocalDateTime, outcome: String, duration: Long?, ttfb: Long?, input: Long? = null, output: Long? = null,
        deadline: LocalDateTime = startedAt.plusMinutes(10)) {
        jdbc.update("""insert into tbl_serving_usage(id, key_id, access_id, cluster_id, user_id, model, started_at, deadline_at, finished_at, duration_millis,
            http_status, outcome, input_tokens, output_tokens, usage_missing, created_at, updated_at, ttfb_millis) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            UUID.randomUUID(), s.identity.keyId, s.identity.accessId, s.identity.clusterId, s.identity.userId, model, startedAt, deadline,
            duration?.let { startedAt.plusNanos(it * 1_000_000) }, duration, if (outcome == "FAILED") 500 else if (duration != null) 200 else null,
            outcome, input, output, input == null, utcNow(), utcNow(), ttfb)
    }
    private fun rejection(s: Setup, model: String?, reason: String, minute: LocalDateTime, count: Int) {
        jdbc.update("insert into tbl_serving_rejection(id, key_id, access_id, cluster_id, user_id, model, reason, minute, count, created_at) values (?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(), s.identity.keyId, s.identity.accessId, s.identity.clusterId, s.identity.userId, model, reason, minute, count, utcNow())
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
