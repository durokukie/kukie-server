package com.duro.kukie.serving.e2e

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.port.out.CounterTarget
import com.duro.kukie.serving.application.port.out.CounterTransport
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "serving.relay.allowed-hosts=a.example.com,b.example.com",
    "serving.relay.max-connections=2", "serving.relay.max-connections-per-key=1",
    "serving.relay.idle-seconds=1", "serving.relay.total-seconds=3",
])
@Import(ServingE2EConfig::class)
class ServingEndToEndTest : IntegrationTest() {
    @LocalServerPort private var port = 0
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var counters: LocalHttpsCounters
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val mapper = JsonMapper.builder().build()

    @AfterEach fun closeClient() { client.close(); counters.calls.clear() }

    @Test
    fun `기존 계정의 팀과 A B API 권한 키 HTTPS 중계 사용량 폐기를 함께 검증한다`() {
        // given
        val s = setup()
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val logs = ListAppender<ILoggingEvent>().apply { start() }
        root.addAppender(logs)
        try {
            // when: 실제 로그인·초대·수락·구성·개인 키 발급
            val login = call("POST", "/auth/login", null, mapOf("email" to s.user.user.email.value, "password" to UserFixture.DEFAULT_PASSWORD))
            login.statusCode() shouldBe 200
            val userToken = json(login).get("accessToken").asString()
            val a = grant(s.a, userToken, s.user.user.email.value)
            val b = grant(s.b, userToken, s.user.user.email.value)
            fieldIds(teams(userToken), "id") shouldBe setOf(s.ownTeam.toString())
            call("GET", "/teams/${s.ownTeam}/members", userToken).statusCode() shouldBe 200
            call("GET", "/clusters/${s.a.cluster}/serving/accesses", userToken).statusCode() shouldBe 404
            call("GET", "/clusters/${s.b.cluster}/serving/accesses", s.a.admin.accessToken).statusCode() shouldBe 404
            val allowed = call("GET", "/serving/clusters", userToken)
            fieldIds(json(allowed), "clusterId") shouldBe setOf(s.a.cluster.toString(), s.b.cluster.toString())
            allowed.body().contains("apiServer") shouldBe false
            allowed.body().contains("credential") shouldBe false
            val missingEndpoint = call("GET", "/clusters/${s.a.cluster}/serving/endpoint", s.a.admin.accessToken)
            missingEndpoint.statusCode() shouldBe 200
            missingEndpoint.body().isEmpty() shouldBe true
            configure(s.a, "a")
            configure(s.b, "b")
            val ka = issue(userToken, a)
            val kb = issue(userToken, b)

            // then: 같은 이름의 모델도 키 대상에만 전달하고 서비스 인증으로 바꾼다.
            val models = call("GET", "/serving/v1/models", ka.token)
            models.statusCode() shouldBe 200
            models.body().contains("hidden") shouldBe false
            call("POST", "/serving/v1/chat/completions", ka.token, chat(), cookie = "__Host-kukie_access=private-cookie-canary").body().contains("reply-a-canary") shouldBe true
            call("POST", "/serving/v1/chat/completions", kb.token, chat()).body().contains("reply-b-canary") shouldBe true
            val streamed = call("POST", "/serving/v1/chat/completions", kb.token, chat(stream = true))
            streamed.statusCode() shouldBe 200
            streamed.body() shouldBe LocalHttpsCounters.sse("b")
            counters.calls.map { it.provider } shouldBe listOf("a", "a", "b", "b")
            counters.calls.forEach { it.authorization shouldBe "Bearer service-${it.provider}"; it.cookie shouldBe null }
            val ua = json(call("GET", "/serving/accesses/$a/usage", userToken))
            val ub = json(call("GET", "/serving/accesses/$b/usage", userToken))
            ua.get("totals").get("requests").longValue() shouldBe 2
            ua.get("totals").get("inputTokens").longValue() shouldBe 11
            ub.get("totals").get("requests").longValue() shouldBe 2
            ub.get("totals").get("inputTokens").longValue() shouldBe 16
            ub.get("totals").get("outputTokens").longValue() shouldBe 29
            ua.get("byUser").size() shouldBe 0
            json(call("GET", "/clusters/${s.a.cluster}/serving/usage", s.a.admin.accessToken)).get("totals").get("requests").longValue() shouldBe 2
            call("GET", "/serving/accesses/$a/usage", s.b.admin.accessToken).statusCode() shouldBe 404
            call("GET", "/serving/v1/models?clusterId=${s.b.cluster}", ka.token).statusCode() shouldBe 400
            call("POST", "/serving/v1/chat/completions", ka.token, chat(model = "hidden")).statusCode() shouldBe 403

            // when: 권한 취소·재초대·키 만료·본인 폐기를 순서대로 실행한다.
            call("DELETE", "/clusters/${s.a.cluster}/serving/accesses/$a", s.a.admin.accessToken).statusCode() shouldBe 204
            call("GET", "/serving/v1/models", ka.token).statusCode() shouldBe 401
            call("GET", "/serving/v1/models", kb.token).statusCode() shouldBe 200
            val freshAccess = grant(s.a, userToken, s.user.user.email.value)
            (freshAccess != a) shouldBe true
            val freshKey = issue(userToken, freshAccess)
            call("GET", "/serving/v1/models", ka.token).statusCode() shouldBe 401
            call("GET", "/serving/v1/models", freshKey.token).statusCode() shouldBe 200
            jdbc.update("update tbl_serving_api_key set expires_at = ? where id = ?", utcNow().minusSeconds(1), freshKey.id)
            call("GET", "/serving/v1/models", freshKey.token).statusCode() shouldBe 401
            call("DELETE", "/serving/accesses/$b/keys/${kb.id}", userToken).statusCode() shouldBe 204
            call("GET", "/serving/v1/models", kb.token).statusCode() shouldBe 401
            call("GET", "/serving/v1/models", null).statusCode() shouldBe 401
            call("GET", "/serving/v1/models", "kk_" + "x".repeat(43)).statusCode() shouldBe 401
            fieldIds(teams(userToken), "id") shouldBe setOf(s.ownTeam.toString())

            // then: 원장과 표준 실행 로그에는 본문·원문 개인/서비스 키가 없다.
            val stored = jdbc.query("select row_to_json(u)::text from tbl_serving_usage u") { rs, _ -> rs.getString(1) }.joinToString()
            val logged = logs.list.joinToString { it.formattedMessage }
            for (canary in listOf("private-prompt-canary", "reply-a-canary", "reply-b-canary", ka.token, kb.token, freshKey.token, "service-a", "service-b", "private-cookie-canary")) {
                stored.contains(canary) shouldBe false
                logged.contains(canary) shouldBe false
            }
        } finally { root.detachAppender(logs); logs.stop() }
    }

    @Test
    fun `실제 HTTPS 상위 장애와 끊긴 SSE 및 누락을 원장까지 검증한다`() {
        // given
        val s = setup()
        val access = grant(s.a, s.user.accessToken, s.user.user.email.value)
        configure(s.a, "a")
        val key = issue(s.user.accessToken, access)
        // when then
        for ((mode, expected) in listOf("rate" to 429, "unavailable" to 503, "redirect" to 502, "drop" to 502, "idle" to 504)) {
            val response = call("POST", "/serving/v1/chat/completions", key.token, chat(mode = mode, stream = mode == "idle"))
            response.statusCode() shouldBe expected
            response.body().contains("private-upstream-error") shouldBe false
        }
        val incomplete = call("POST", "/serving/v1/chat/completions", key.token, chat(mode = "truncate", stream = true))
        incomplete.statusCode() shouldBe 200
        incomplete.body().contains("[DONE]") shouldBe false
        val result = json(call("GET", "/serving/accesses/$access/usage", s.user.accessToken)).get("totals")
        result.get("requests").longValue() shouldBe 6
        result.get("failed").longValue() shouldBe 6
        result.get("missingUsage").longValue() shouldBe 6
        result.get("inProgress").longValue() shouldBe 0
        counters.calls.size shouldBe 6
    }

    @Test
    fun `실제 클라이언트 스트림 취소와 키 동시 제한 후 슬롯이 돌아온다`() {
        // given
        val s = setup()
        val access = grant(s.a, s.user.accessToken, s.user.user.email.value)
        configure(s.a, "a")
        val key = issue(s.user.accessToken, access)
        // when
        val stream = client.send(request("POST", "/serving/v1/chat/completions", key.token, chat(mode = "hold", stream = true)), HttpResponse.BodyHandlers.ofInputStream())
        stream.statusCode() shouldBe 200
        stream.body().readNBytes(8).size shouldBe 8
        call("POST", "/serving/v1/chat/completions", key.token, chat()).statusCode() shouldBe 429
        stream.body().close()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (System.nanoTime() < deadline && jdbc.queryForObject("select count(*) from tbl_serving_usage where outcome = 'IN_PROGRESS'", Long::class.java) != 0L) Thread.sleep(20)
        // then
        jdbc.queryForObject("select outcome from tbl_serving_usage", String::class.java) shouldBe "CANCELLED"
        call("POST", "/serving/v1/chat/completions", key.token, chat()).statusCode() shouldBe 200
        val totals = json(call("GET", "/serving/accesses/$access/usage", s.user.accessToken)).get("totals")
        totals.get("requests").longValue() shouldBe 2
        totals.get("completed").longValue() shouldBe 1
        totals.get("failed").longValue() shouldBe 1
        totals.get("missingUsage").longValue() shouldBe 1
    }

    private fun fieldIds(tree: JsonNode, field: String): Set<String> = (0 until tree.size()).map { tree.get(it).get(field).asString() }.toSet()

    private fun teams(token: String): JsonNode {
        val response = call("GET", "/teams", token)
        response.statusCode() shouldBe 200
        val tree = json(response)
        check(tree.isArray) { "팀 목록의 응답 형태가 배열이 아닙니다: $tree" }
        return tree
    }
    private fun setup(): Scenario {
        val a = loggedInUser(UserFixture.user(email = "a-admin@example.com"))
        val b = loggedInUser(UserFixture.user(email = "b-admin@example.com"))
        val user = loggedInUser(UserFixture.user(email = "client@example.com"))
        fun provider(admin: LoggedInUser): Provider {
            val team = savedTeam(admin.user.id to TeamRole.ADMIN)
            val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team))
            return Provider(admin, cluster.id)
        }
        return Scenario(provider(a), provider(b), user, savedTeam(user.user.id to TeamRole.ADMIN))
    }
    private fun configure(provider: Provider, label: String) {
        call("PUT", "/clusters/${provider.cluster}/serving/endpoint", provider.admin.accessToken, mapOf("baseUrl" to "https://$label.example.com/v1", "serviceToken" to "service-$label", "models" to listOf("shared"))).statusCode() shouldBe 204
    }
    private fun grant(provider: Provider, userToken: String, email: String): UUID {
        val invitation = call("POST", "/clusters/${provider.cluster}/serving/invitations", provider.admin.accessToken, mapOf("email" to email))
        invitation.statusCode() shouldBe 201
        val id = UUID.fromString(json(invitation).get("id").asString())
        call("POST", "/serving/invitations/$id/accept", userToken).statusCode() shouldBe 204
        return id
    }
    private fun issue(userToken: String, access: UUID): Key {
        val issued = call("POST", "/serving/accesses/$access/keys", userToken, mapOf("name" to "e2e"))
        issued.statusCode() shouldBe 201
        issued.headers().firstValue("Cache-Control").orElse("") shouldBe "no-store"
        val json = json(issued)
        return Key(UUID.fromString(json.get("key").get("id").asString()), json.get("token").asString())
    }
    private fun chat(mode: String = "normal", stream: Boolean = false, model: String = "shared") = mapOf("model" to model, "messages" to listOf(mapOf("role" to "user", "content" to "private-prompt-canary")), "stream" to stream, "stream_options" to mapOf("include_usage" to true), "fixture_mode" to mode)
    private fun request(method: String, path: String, token: String?, body: Any?, cookie: String? = null): HttpRequest {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(10))
        token?.let { builder.header("Authorization", "Bearer $it") }
        cookie?.let { builder.header("Cookie", it) }
        builder.header("Content-Type", "application/json")
        return builder.method(method, body?.let { HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(it)) } ?: HttpRequest.BodyPublishers.noBody()).build()
    }
    private fun call(method: String, path: String, token: String?, body: Any? = null, cookie: String? = null): HttpResponse<String> = client.send(request(method, path, token, body, cookie), HttpResponse.BodyHandlers.ofString())
    private fun json(response: HttpResponse<String>): JsonNode = mapper.readTree(response.body())
    private data class Provider(val admin: LoggedInUser, val cluster: UUID)
    private data class Scenario(val a: Provider, val b: Provider, val user: LoggedInUser, val ownTeam: UUID)
    private class Key(val id: UUID, val token: String)
}

@TestConfiguration
class ServingE2EConfig {
    @Bean fun localHttpsCounters() = LocalHttpsCounters()
    @Bean @Primary fun e2eTransport(counters: LocalHttpsCounters): CounterTransport = counters
}
