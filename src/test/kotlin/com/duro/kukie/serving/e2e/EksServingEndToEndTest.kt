package com.duro.kukie.serving.e2e

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.application.EksCredential
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.domain.*
import com.duro.kukie.cluster.infrastructure.AwsEksAccessGateway
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.IssueServingApiKeyService
import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingEndpointRepository
import com.duro.kukie.support.FakeEksAccessGateway
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.*
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "serving.relay.max-connections=2", "serving.relay.max-connections-per-key=1",
    "serving.relay.idle-seconds=1", "serving.relay.total-seconds=3",
])
class EksServingEndToEndTest : IntegrationTest() {
    @LocalServerPort private var port = 0
    @Autowired private lateinit var gateway: FakeEksAccessGateway
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var connections: AwsConnectionRepository
    @Autowired private lateinit var cipher: CredentialCipher
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var endpoints: ServingEndpointRepository
    @Autowired private lateinit var issue: IssueServingApiKeyService
    @Autowired private lateinit var jdbc: JdbcTemplate
    private val mapper = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val threads = Executors.newVirtualThreadPerTaskExecutor()
    private val calls = CopyOnWriteArrayList<Seen>()
    private val catalogHeaders = CopyOnWriteArrayList<String?>()
    private var listStatus = 200
    private var phase = "Ready"
    private var deletion = false
    private var continuation = false
    private var ready = CountDownLatch(1)
    private var closed = CountDownLatch(1)

    @BeforeEach fun start() {
        upstream.executor = threads
        upstream.createContext("/apis/") { e ->
            catalogHeaders.add(e.requestHeaders.getFirst("Authorization"))
            val item = """{"metadata":{"name":"gpt-oss-20b"${if (deletion) ",\"deletionTimestamp\":\"2026-01-01T00:00:00Z\"" else ""}},"spec":{"source":{"huggingface":"openai/gpt-oss-20b"}},"status":{"phase":"$phase"}}"""
            val list = """{"metadata":{${if (continuation) "\"continue\":\"more\"" else ""}},"items":[$item,{"metadata":{"name":"evil/../../x"},"spec":{"source":{"huggingface":"org/model"}},"status":{"phase":"Ready"}}]}"""
            e.answer(listStatus, "application/json", list)
        }
        upstream.createContext("/api/v1/") { e ->
            val body = e.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val json = mapper.readTree(body)
            calls.add(Seen(e.requestURI.toString(), e.requestHeaders.getFirst("Authorization"), e.requestHeaders.getFirst("Cookie"), body))
            val mode = json.path("fixture_mode").asString("")
            try {
                when (mode) {
                    "rate" -> e.answer(429, "application/json", "secret-upstream-error")
                    "unavailable" -> e.answer(503, "application/json", "secret-upstream-error")
                    "redirect" -> { e.responseHeaders.add("Location", "http://127.0.0.1/forbidden"); e.answer(302, "application/json", "secret-upstream-error") }
                    "idle" -> { e.responseHeaders.add("Content-Type", "application/json"); e.sendResponseHeaders(200, 0); e.responseBody.flush(); Thread.sleep(2200); e.responseBody.write("{}".toByteArray()); e.close() }
                    "hold" -> {
                        e.responseHeaders.add("Content-Type", "text/event-stream"); e.sendResponseHeaders(200, 0)
                        while (true) { e.responseBody.write(": heartbeat\n\n".toByteArray()); e.responseBody.flush(); ready.countDown(); Thread.sleep(50) }
                    }
                    else -> if (json.path("stream").asBoolean(false)) e.answer(200, "text/event-stream", SSE) else e.answer(200, "application/json", JSON)
                }
            } catch (_: Exception) { e.close(); closed.countDown() }
        }
        upstream.start()
        gateway.servingBackend = AwsEksAccessGateway(StubSts(), ClusterAwsProperties())
        gateway.servingBackendEndpoint = EksClusterEndpoint("http://127.0.0.1:${upstream.address.port}", "")
    }

    @AfterEach fun stop() { upstream.stop(0); threads.shutdownNow(); client.shutdownNow() }

    @Test fun `별도 카운터 설정 없이 EKS 모델 목록 일반 응답 SSE와 키별 사용량을 연결한다`() {
        // given
        val s = setup()
        // when
        val models = call("/serving/v1/models", s.key)
        val json = call("/serving/v1/chat/completions", s.key, chat())
        val stream = call("/serving/v1/chat/completions", s.key, chat(stream = true))
        // then
        models.statusCode() shouldBe 200
        mapper.readTree(models.body()).path("data").size() shouldBe 1
        models.body().contains("gpt-oss-20b") shouldBe true
        json.body() shouldBe JSON
        stream.body() shouldBe SSE
        calls.size shouldBe 2
        calls.forEach {
            it.path shouldBe "/api/v1/namespaces/llm/services/gpt-oss-20b:http/proxy/v1/chat/completions"
            it.auth!!.startsWith("Bearer k8s-aws-v1.") shouldBe true
            it.auth.contains(s.key) shouldBe false
            it.cookie shouldBe null
            mapper.readTree(it.body).path("model").asString() shouldBe "openai/gpt-oss-20b"
        }
        catalogHeaders.all { it?.startsWith("Bearer k8s-aws-v1.") == true } shouldBe true
        endpoints.findByClusterId(s.cluster.id) shouldBe null
        eventually { jdbc.queryForObject("select count(*) from tbl_serving_usage where outcome='COMPLETED'", Int::class.java) shouldBe 3 }
        val info = call("/serving/accesses/${s.access.id}/info", s.user.accessToken)
        mapper.readTree(info.body()).path("connectionMode").asString() shouldBe "EKS_PROXY"
        mapper.readTree(info.body()).path("models").get(0).asString() shouldBe "gpt-oss-20b"
        listOf("credential", "caData", "apiServer", "externalId", "k8s-aws-v1").forEach { info.body().contains(it) shouldBe false }
        call("/clusters/${s.cluster.id}/serving/accesses", s.user.accessToken).statusCode() shouldBe 404
        val usage = mapper.readTree(call("/serving/accesses/${s.access.id}/usage", s.user.accessToken).body()).path("totals")
        usage.path("inputTokens").asLong() shouldBe 14
        usage.path("outputTokens").asLong() shouldBe 26
    }

    @Test fun `다른 클러스터의 키 모델 변조와 폐기 키는 상위 모델 호출 전에 차단한다`() {
        // given
        val s = setup()
        // when then
        call("/serving/v1/chat/completions", s.key, chat().replace("gpt-oss-20b", "other")).statusCode() shouldBe 403
        calls.size shouldBe 0
        val revoked = accesses.findById(s.access.id).get().apply { revoke() }; accesses.save(revoked)
        val before = catalogHeaders.size
        call("/serving/v1/models", s.key).statusCode() shouldBe 401
        catalogHeaders.size shouldBe before
    }

    @Test fun `준비 중 모델은 목록에서 제외하고 요청은 503으로 기록한다`() {
        // given
        val s = setup(); phase = "Loading"
        // when then
        mapper.readTree(call("/serving/v1/models", s.key).body()).path("data").size() shouldBe 0
        call("/serving/v1/chat/completions", s.key, chat()).statusCode() shouldBe 503
        calls.size shouldBe 0
        eventually { jdbc.queryForObject("select count(*) from tbl_serving_usage where outcome='FAILED'", Int::class.java) shouldBe 1 }
    }

    @Test fun `EKS 권한이 제한되거나 연결이 해제되면 자동 중계를 허용하지 않는다`() {
        // given
        val s = setup()
        // when then
        val connection = connections.findAll().single(); connection.connect("123456789012", utcNow(), null, AccessLevel.LIMITED, false, null)
        connections.save(connection)
        call("/serving/v1/models", s.key).statusCode() shouldBe 503
        catalogHeaders.size shouldBe 0
        calls.size shouldBe 0
    }

    @Test fun `모델 목록 권한 오류와 잘린 목록은 외부 주소로 우회하지 않고 실패한다`() {
        // given
        val s = setup(); listStatus = 403
        // when then
        call("/serving/v1/models", s.key).statusCode() shouldBe 502
        listStatus = 200; continuation = true
        call("/serving/v1/models", s.key).statusCode() shouldBe 502
        calls.size shouldBe 0
    }

    @ParameterizedTest @ValueSource(strings = ["rate", "unavailable", "redirect", "idle"])
    fun `카운터 없는 경로도 상위 오류 redirect 유휴 초과를 처리한다`(mode: String) {
        // given
        val s = setup()
        // when
        val response = call("/serving/v1/chat/completions", s.key, chat(mode = mode))
        // then
        response.statusCode() shouldBe when (mode) { "rate" -> 429; "unavailable" -> 503; "idle" -> 504; else -> 502 }
        response.body().contains("secret-upstream-error") shouldBe false
        calls.size shouldBe 1
        eventually { jdbc.queryForObject("select count(*) from tbl_serving_usage where outcome <> 'IN_PROGRESS'", Int::class.java) shouldBe 1 }
    }

    @Test fun `EKS 스트리밍의 동시 상한과 취소도 기록한다`() {
        // given
        val s = setup()
        val request = request("/serving/v1/chat/completions", s.key, chat(stream = true, mode = "hold"))
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        ready.await(3, TimeUnit.SECONDS) shouldBe true
        // when then
        call("/serving/v1/chat/completions", s.key, chat()).statusCode() shouldBe 429
        response.body().readNBytes(13)
        response.body().close()
        closed.await(4, TimeUnit.SECONDS) shouldBe true
        eventually { jdbc.queryForObject("select count(*) from tbl_serving_usage where outcome='CANCELLED'", Int::class.java) shouldBe 1 }
        call("/serving/v1/chat/completions", s.key, chat()).statusCode() shouldBe 200
    }

    @Test fun `같은 모델 이름이어도 키가 속한 EKS 클러스터 자격만 사용한다`() {
        // given
        val a = setup(); val b = setup()
        // when
        call("/serving/v1/chat/completions", a.key, chat()).statusCode() shouldBe 200
        val ac = gateway.servingClusters.toList(); gateway.servingClusters.clear()
        call("/serving/v1/chat/completions", b.key, chat()).statusCode() shouldBe 200
        // then
        ac.distinct().size shouldBe 1
        gateway.servingClusters.distinct().size shouldBe 1
        (ac.first() != gateway.servingClusters.first()) shouldBe true
        eventually { jdbc.queryForObject("select count(distinct cluster_id) from tbl_serving_usage where outcome='COMPLETED'", Int::class.java) shouldBe 2 }
    }

    @Test fun `명시적 외부 카운터 설정은 EKS 자동 연결보다 우선한다`() {
        // given
        val s = setup()
        endpoints.save(com.duro.kukie.serving.domain.ServingEndpoint(s.cluster.id, "https://external.example/v1", cipher.encrypt(mapOf("token" to "test-service")), listOf("manual-model")))
        // when
        val info = mapper.readTree(call("/serving/accesses/${s.access.id}/info", s.user.accessToken).body())
        // then
        info.path("connectionMode").asString() shouldBe "HTTPS_COUNTER"
        info.path("models").get(0).asString() shouldBe "manual-model"
        catalogHeaders.size shouldBe 0
        calls.size shouldBe 0
    }

    @Test fun `오븐이 없거나 삭제 중인 모델은 빈 목록과 준비 안 됨으로 안내한다`() {
        // given
        val s = setup(); deletion = true
        // when then
        mapper.readTree(call("/serving/v1/models", s.key).body()).path("data").size() shouldBe 0
        val absent = call("/serving/v1/chat/completions", s.key, chat())
        absent.statusCode() shouldBe 503
        absent.body().contains("SERVING_MODEL_UNAVAILABLE") shouldBe true
        listStatus = 404
        mapper.readTree(call("/serving/v1/models", s.key).body()).path("data").size() shouldBe 0
        calls.size shouldBe 0
    }

    @Test fun `EKS 스트리밍이 계속 응답해도 전체 시간 상한을 적용한다`() {
        // given
        val s = setup()
        // when
        val response = call("/serving/v1/chat/completions", s.key, chat(stream = true, mode = "hold"))
        // then
        response.statusCode() shouldBe 200
        response.body().contains("[DONE]") shouldBe false
        eventually { jdbc.queryForObject("select count(*) from tbl_serving_usage where outcome='TIMEOUT' and wire_status=200", Int::class.java) shouldBe 1 }
        closed.await(4, TimeUnit.SECONDS) shouldBe true
    }

    private fun setup(): Setup {
        val admin = loggedInUser(UserFixture.user(email = "admin-${UUID.randomUUID()}@example.com"))
        val user = loggedInUser(UserFixture.user(email = "client-${UUID.randomUUID()}@example.com"))
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val connection = connections.save(AwsConnection(team, admin.user.id).apply { connect("123456789012", utcNow(), null, AccessLevel.FULL, false, null) })
        val credential = EksCredential("123456789012", connection.externalId, "ap-northeast-2", "demo-${UUID.randomUUID().toString().take(8)}")
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team, credential = cipher.encrypt(credential.toMap()), provider = ClusterProvider.EKS, accessLevel = AccessLevel.FULL, fingerprint = UUID.randomUUID().toString()))
        val access = accesses.save(ServingAccess(cluster.id, user.user.email, admin.user.id, utcNow().plusDays(7)).apply { accept(user.user.id, user.user.email, utcNow()) })
        val key = issue(IssueServingApiKeyCommand(access.id, user.user.id, "demo", 30)).token
        return Setup(user, cluster, access, key)
    }
    private fun call(path: String, key: String, body: String? = null): HttpResponse<String> = client.send(request(path, key, body), HttpResponse.BodyHandlers.ofString())
    private fun request(path: String, key: String, body: String?): HttpRequest = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        .timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer $key").header("Cookie", "__Host-kukie_access=must-not-forward")
        .header("Content-Type", "application/json").apply { if (body != null) POST(HttpRequest.BodyPublishers.ofString(body)) else GET() }.build()
    private fun chat(stream: Boolean = false, mode: String = ""): String = """{"model":"gpt-oss-20b","messages":[{"role":"user","content":"private-prompt"}],"stream":$stream,"fixture_mode":"$mode"}"""
    private fun eventually(check: () -> Unit) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (true) { try { check(); return } catch (e: AssertionError) { if (System.nanoTime() >= until) throw e; Thread.sleep(20) } }
    }
    private fun HttpExchange.answer(status: Int, type: String, body: String) { responseHeaders.add("Content-Type", type); val bytes = body.toByteArray(); sendResponseHeaders(status, bytes.size.toLong()); responseBody.use { it.write(bytes) }; close() }
    private class StubSts : StsClient {
        override fun assumeRole(request: AssumeRoleRequest): AssumeRoleResponse = AssumeRoleResponse.builder().credentials(Credentials.builder().accessKeyId("AKIDEXAMPLE").secretAccessKey("test-only").sessionToken("test-session").expiration(Instant.now().plusSeconds(3600)).build()).build()
        override fun serviceName() = "sts"
        override fun close() = Unit
    }
    private data class Setup(val user: LoggedInUser, val cluster: Cluster, val access: ServingAccess, val key: String)
    private data class Seen(val path: String, val auth: String?, val cookie: String?, val body: String)
    private companion object {
        const val JSON = """{"id":"reply","choices":[{"message":{"role":"assistant","content":"hello"}}],"usage":{"prompt_tokens":11,"completion_tokens":22}}"""
        const val SSE = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\r\n\r\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4}}\r\n\r\ndata: [DONE]\r\n\r\n"
    }
}
