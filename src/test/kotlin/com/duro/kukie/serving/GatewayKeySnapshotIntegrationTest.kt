package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.IssueServingApiKeyService
import com.duro.kukie.serving.application.RevokeMyServingApiKeyService
import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import com.duro.kukie.serving.domain.*
import com.duro.kukie.serving.infrastructure.ServingKeyTokens
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.util.UUID

@TestPropertySource(properties = ["internal.gateway-token=test-gateway-token"])
class GatewayKeySnapshotIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var endpoints: ServingEndpointRepository
    @Autowired private lateinit var usage: ServingUsageRepository
    @Autowired private lateinit var issue: IssueServingApiKeyService
    @Autowired private lateinit var revoke: RevokeMyServingApiKeyService
    @Autowired private lateinit var tokens: ServingKeyTokens
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `키 스냅샷은 지금 쓸 수 있는 키의 해시와 클러스터별 허용 모델만 준다`() {
        // given
        val a = setup("a", listOf("shared", "other")); val b = setup("b", listOf("solo"))
        val usable = issue(IssueServingApiKeyCommand(a.first.id, a.second, "usable", 90))
        issue(IssueServingApiKeyCommand(a.first.id, a.second, "revoked", 90)).also { revoke(a.first.id, a.second, it.key.id) }
        val expired = issue(IssueServingApiKeyCommand(a.first.id, a.second, "expired", 90))
        jdbc.update("update tbl_serving_api_key set expires_at = ? where id = ?", utcNow().minusMinutes(1), expired.key.id)
        issue(IssueServingApiKeyCommand(b.first.id, b.second, "inactive", 90))
        accesses.findById(b.first.id).get().also { it.revoke() }.let { accesses.save(it) }
        // when then
        post("/key-snapshot", emptyMap<String, Any>()).andExpect {
            status { isOk() }; header { string("Cache-Control", "no-store") }
            jsonPath("$.keys") { value(hasSize<Any>(1)) }
            jsonPath("$.keys[0].keyId") { value(usable.key.id.toString()) }
            jsonPath("$.keys[0].tokenSha256") { value(tokens.hash(usable.token)) }
            jsonPath("$.keys[0].clusterId") { value(a.first.clusterId.toString()) }
            jsonPath("$.keys[0].expiresAt") { exists() }
            jsonPath("$.keys[0].token") { doesNotExist() }
            jsonPath("$.clusterModels.${a.first.clusterId}[0]") { value("shared") }
            jsonPath("$.clusterModels.${a.first.clusterId}[1]") { value("other") }
            jsonPath("$.clusterModels.${b.first.clusterId}") { doesNotExist() }
        }
    }

    @Test
    fun `게이트웨이 자격이 없으면 키 스냅샷과 사용량 기록을 거절한다`() {
        // when then
        for (token in listOf("test-internal-token", "", "invalid")) {
            post("/key-snapshot", emptyMap<String, Any>(), token).andExpect { status { isUnauthorized() } }
            post("/usage", mapOf("records" to listOf(record(UUID.randomUUID()))), token).andExpect { status { isUnauthorized() } }
        }
        usage.count() shouldBe 0
    }

    @Test
    fun `사용량 배치를 저장하고 같은 배치를 다시 보내도 한 행만 남는다`() {
        // given
        val s = setup("a")
        val key = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90)).key
        val requestId = UUID.randomUUID()
        val startedAt = Instant.now().minusSeconds(30)
        val body = mapOf("records" to listOf(record(key.id, requestId, startedAt = startedAt)))
        // when
        post("/usage", body).andExpect { status { isNoContent() } }
        post("/usage", body).andExpect { status { isNoContent() } }
        // then
        usage.count() shouldBe 1
        usage.findById(requestId).get().let {
            it.keyId shouldBe key.id; it.accessId shouldBe s.first.id; it.clusterId shouldBe s.first.clusterId; it.userId shouldBe s.second
            it.model shouldBe "shared"; it.outcome shouldBe "COMPLETED"; it.httpStatus shouldBe 200; it.wireStatus shouldBe 200
            it.inputTokens shouldBe 3; it.outputTokens shouldBe 7; it.usageMissing shouldBe false; it.durationMillis shouldBe 1234
            Duration.between(it.startedAt, it.finishedAt).toMillis() shouldBe 1234
        }
    }

    @Test
    fun `폐기된 키의 기록도 저장하고 상태 없는 실패와 토큰 누락을 기존 규칙대로 남긴다`() {
        // given
        val s = setup("a")
        val key = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90)).key
        revoke(s.first.id, s.second, key.id)
        val requestId = UUID.randomUUID()
        // when
        post("/usage", mapOf("records" to listOf(record(key.id, requestId) + mapOf(
            "outcome" to "FAILED", "status" to 0, "tokensKnown" to false, "inputTokens" to null, "outputTokens" to null,
        )))).andExpect { status { isNoContent() } }
        // then
        usage.findById(requestId).get().let {
            it.outcome shouldBe "FAILED"; it.httpStatus shouldBe 502; it.wireStatus shouldBe null
            it.usageMissing shouldBe true; it.inputTokens shouldBe null
        }
    }

    @Test
    fun `없는 키와 허용 시간 밖 기록은 그 건만 건너뛴다`() {
        // given
        val s = setup("a")
        val key = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90)).key
        val kept = UUID.randomUUID()
        val now = Instant.now()
        // when
        post("/usage", mapOf("records" to listOf(
            record(key.id, kept),
            record(UUID.randomUUID()),
            record(key.id, startedAt = now.minus(Duration.ofDays(1)).minusSeconds(60)),
            record(key.id, startedAt = now.plus(Duration.ofMinutes(6))),
        ))).andExpect { status { isNoContent() } }
        // then
        usage.findAll().map { it.id } shouldBe listOf(kept)
    }

    @Test
    fun `잘못된 사용량 기록과 500건을 넘는 배치는 400으로 거절한다`() {
        // given
        val s = setup("a")
        val key = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90)).key
        // when then
        for (bad in listOf(
            record(key.id) + ("status" to 42),
            record(key.id) + ("inputTokens" to -1),
            record(key.id) + ("outcome" to "IN_PROGRESS"),
            record(key.id) + ("outcome" to "COMPLETED") + ("status" to 500),
            record(key.id) + ("model" to ""),
            record(key.id) + ("model" to "m".repeat(201)),
            record(key.id) + ("durationMillis" to 86_400_001),
            record(key.id) + ("durationMillis" to -1),
            record(key.id) - "requestId",
        )) post("/usage", mapOf("records" to listOf(bad))).andExpect { status { isBadRequest() } }
        post("/usage", mapOf("records" to emptyList<Any>())).andExpect { status { isBadRequest() } }
        post("/usage", mapOf("records" to listOf(null))).andExpect { status { isBadRequest() } }
        post("/usage", emptyMap<String, Any>()).andExpect { status { isBadRequest() } }
        post("/usage", mapOf("records" to (1..501).map { record(key.id) }))
            .andExpect { status { isBadRequest() } }
        usage.count() shouldBe 0
        // 500건은 받는다
        post("/usage", mapOf("records" to (1..500).map { record(key.id) })).andExpect { status { isNoContent() } }
        usage.count() shouldBe 500
    }

    @Test
    fun `첫 바이트 시간은 있으면 저장하고 옛 게이트웨이처럼 빠지면 비운다`() {
        // given
        val s = setup("a")
        val key = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90)).key
        val withTtfb = UUID.randomUUID(); val without = UUID.randomUUID()
        // when
        post("/usage", mapOf("records" to listOf(record(key.id, withTtfb) + ("ttfbMillis" to 250), record(key.id, without)))).andExpect { status { isNoContent() } }
        // then
        usage.findById(withTtfb).get().ttfbMillis shouldBe 250
        usage.findById(without).get().ttfbMillis shouldBe null
    }

    @Test
    fun `거절 집계를 저장하고 같은 id를 다시 보내도 한 행만 남긴다`() {
        // given
        val s = setup("a")
        val key = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90)).key
        val id = UUID.randomUUID()
        val minute = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        val body = mapOf("records" to emptyList<Any>(), "rejections" to listOf(rejection(key.id, id, minute.plusSeconds(17)), rejection(UUID.randomUUID())))
        // when
        post("/usage", body).andExpect { status { isNoContent() } }
        post("/usage", body).andExpect { status { isNoContent() } }
        // then
        jdbc.queryForMap("select key_id, access_id, cluster_id, user_id, model, reason, minute, count from tbl_serving_rejection").let {
            it["key_id"] shouldBe key.id; it["access_id"] shouldBe s.first.id; it["cluster_id"] shouldBe s.first.clusterId; it["user_id"] shouldBe s.second
            it["model"] shouldBe "shared"; it["reason"] shouldBe "concurrency_limit"; it["count"] shouldBe 3
            (it["minute"] as java.sql.Timestamp).toLocalDateTime() shouldBe java.time.LocalDateTime.ofInstant(minute, java.time.ZoneOffset.UTC)
        }
        jdbc.queryForObject("select count(*) from tbl_serving_rejection where id = ?", Long::class.java, id) shouldBe 1
        usage.count() shouldBe 0
    }

    @Test
    fun `잘못된 거절 집계와 500건을 넘는 거절 배치는 400으로 거절한다`() {
        // given
        val s = setup("a")
        val key = issue(IssueServingApiKeyCommand(s.first.id, s.second, "test", 90)).key
        // when then
        for (bad in listOf(
            rejection(key.id) + ("reason" to "rate_limit"),
            rejection(key.id) + ("count" to 0),
            rejection(key.id) + ("model" to "m".repeat(201)),
            rejection(key.id) - "id",
        )) post("/usage", mapOf("records" to listOf(record(key.id)), "rejections" to listOf(bad))).andExpect { status { isBadRequest() } }
        post("/usage", mapOf("records" to listOf(record(key.id) + ("ttfbMillis" to -1)))).andExpect { status { isBadRequest() } }
        post("/usage", mapOf("records" to emptyList<Any>(), "rejections" to listOf(null))).andExpect { status { isBadRequest() } }
        post("/usage", mapOf("records" to emptyList<Any>(), "rejections" to (1..501).map { rejection(key.id) })).andExpect { status { isBadRequest() } }
        usage.count() shouldBe 0
        jdbc.queryForObject("select count(*) from tbl_serving_rejection", Long::class.java) shouldBe 0
    }

    private fun rejection(keyId: UUID, id: UUID = UUID.randomUUID(), minute: Instant = Instant.now()): Map<String, Any?> = mapOf(
        "id" to id.toString(), "keyId" to keyId.toString(), "model" to "shared", "reason" to "concurrency_limit", "minute" to minute.toString(), "count" to 3,
    )

    private fun post(path: String, body: Any, serviceToken: String = "test-gateway-token") = mockMvc.post("/internal/serving-gateway$path") {
        header("X-Kukie-Internal-Token", serviceToken); contentType = MediaType.APPLICATION_JSON; content = body.toJson()
    }

    private fun record(keyId: UUID, requestId: UUID = UUID.randomUUID(), startedAt: Instant = Instant.now().minusSeconds(10)): Map<String, Any?> = mapOf(
        "requestId" to requestId.toString(), "keyId" to keyId.toString(), "model" to "shared", "startedAt" to startedAt.toString(),
        "durationMillis" to 1234, "outcome" to "COMPLETED", "status" to 200, "tokensKnown" to true, "inputTokens" to 3, "outputTokens" to 7,
    )

    private fun setup(label: String, models: List<String> = listOf("shared")): Pair<ServingAccess, UUID> {
        val admin = loggedInUser(UserFixture.user(email = "$label-admin@example.com"))
        val user = loggedInUser(UserFixture.user(email = "$label-user@example.com"))
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team, name = label))
        val access = accesses.save(ServingAccess(cluster.id, user.user.email, admin.user.id, utcNow().plusDays(7)).also { it.accept(user.user.id, user.user.email, utcNow()) })
        endpoints.save(ServingEndpoint(cluster.id, "https://counter.example/v1", "unused-test-ciphertext", models))
        return access to user.user.id
    }
}
