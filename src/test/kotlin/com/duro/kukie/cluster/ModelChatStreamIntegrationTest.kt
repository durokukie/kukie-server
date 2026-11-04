package com.duro.kukie.cluster

import com.duro.kukie.cluster.application.EksCredential
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.cluster.infrastructure.AwsEksAccessGateway
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.config.properties.JwtProperties
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.support.FakeEksAccessGateway
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_CLUSTER_NAME
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_REGION
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse
import software.amazon.awssdk.services.sts.model.Credentials
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * 진짜 톰캣과 진짜 AwsEksAccessGateway 로 브라우저 → 서버 → 로컬 가짜 API 서버를 잇는다. 끊김 전달과 재디스패치는 MockMvc 로 알 수 없다.
 * 쓸 수 있는 곳에서 내용이 새는지 보려고 Spring MVC 로그를 DEBUG 로 켠다.
 */
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "cluster.gpu.chat-timeout=PT6S",
        "cluster.gpu.chat-heartbeat=PT0.2S",
        "logging.level.org.springframework.web=DEBUG",
    ],
)
class ModelChatStreamIntegrationTest : IntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var fakeEksAccessGateway: FakeEksAccessGateway

    @Autowired
    private lateinit var awsConnectionRepository: AwsConnectionRepository

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var credentialCipher: CredentialCipher

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val api = FakeKubernetes()
    private val browser = HttpClient.newHttpClient()

    @BeforeEach
    fun useRealGatewayAgainstFakeApiServer() {
        api.start()
        fakeEksAccessGateway.servingBackend = AwsEksAccessGateway(StubSts(), ClusterAwsProperties())
        fakeEksAccessGateway.servingBackendEndpoint = EksClusterEndpoint(endpoint = api.url, caData = "")
    }

    @AfterEach
    fun stopApi() {
        api.stop()
    }

    @Test
    fun `답을 받는 대로 흘려보내고 마지막에 사용량과 속도를 알린다`(output: CapturedOutput) {
        // given
        val setup = eksCluster()
        val secondDeltaAllowed = CountDownLatch(1)
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendData("""{"choices":[{"delta":{"reasoning":"생각"}}]}""")
            secondDeltaAllowed.await(5, TimeUnit.SECONDS)
            exchange.sendData("""{"choices":[{"delta":{"content":"안녕"}}]}""")
            exchange.sendData("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""")
            exchange.sendData("""{"choices":[],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}""")
            exchange.sendData("[DONE]")
            exchange.close()
        }

        // when
        val stream = openChat(setup, """{"messages":[{"role":"system","content":"짧게"},{"role":"user","content":"안녕?"}]}""")
        val first = stream.nextEvent()
        // 다음 조각을 모델이 보내기 전에 첫 조각이 도착했다. 어딘가 모아 두었다면 여기서 멈춘다
        first.name shouldBe "delta"
        secondDeltaAllowed.countDown()
        val second = stream.nextEvent()
        val done = stream.nextEvent()

        // then
        stream.status shouldBe 200
        stream.contentType shouldContain "text/event-stream"
        stream.headers.firstValue("X-Accel-Buffering").orElse(null) shouldBe "no"
        stream.headers.firstValue("Cache-Control").orElse(null) shouldContain "no-cache"
        first.json().toString() shouldBe """{"content":null,"reasoning":"생각"}"""
        second.name shouldBe "delta"
        second.json().toString() shouldBe """{"content":"안녕","reasoning":null}"""
        done.name shouldBe "done"
        done.json().run {
            path("usage").toString() shouldBe """{"promptTokens":3,"completionTokens":2,"totalTokens":5}"""
            path("finishReason").asString() shouldBe "stop"
            path("ttftMs").isIntegralNumber shouldBe true
            path("tokensPerSecond").isNumber shouldBe true
        }
        stream.nextEvent().name shouldBe null
        stream.close()

        // 모델 이름은 서버가 ServedModel 에서 정하고 기본값을 넣는다
        val sent = objectMapper.readTree(api.chatBodies.single())
        sent.path("model").asString() shouldBe "openai/gpt-oss-20b"
        sent.path("max_tokens").asInt() shouldBe 512
        sent.has("temperature") shouldBe false
        sent.path("stream").asBoolean() shouldBe true
        sent.path("stream_options").path("include_usage").asBoolean() shouldBe true
        sent.path("messages").size() shouldBe 2
        api.chatPaths.single() shouldBe "/api/v1/namespaces/llm/services/gpt-oss-20b:http/proxy/v1/chat/completions"
        api.chatAuthorizations.single()!! shouldContain "Bearer k8s-aws-v1."

        // 끝난 뒤의 로그는 동작과 토큰 수다
        eventually { output.all shouldContain "outcome=done promptTokens=3 completionTokens=2" }
    }

    @Test
    fun `요청의 최대 토큰과 온도를 모델에 넘긴다`() {
        // given
        val setup = eksCluster()
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendData("[DONE]")
            exchange.close()
        }

        // when
        openChat(setup, """{"messages":[{"role":"user","content":"x"}],"maxTokens":2048,"temperature":0.5}""").use { it.readAll() }

        // then
        val sent = objectMapper.readTree(api.chatBodies.single())
        sent.path("max_tokens").asInt() shouldBe 2048
        sent.path("temperature").asDouble() shouldBe 0.5
    }

    @Test
    fun `모델이 말이 없는 동안 keep-alive 를 보내 첫 바이트와 간격을 지킨다`() {
        // given
        val setup = eksCluster()
        val answer = CountDownLatch(1)
        api.chat = { exchange ->
            answer.await(5, TimeUnit.SECONDS)
            exchange.startSse()
            exchange.sendData("[DONE]")
            exchange.close()
        }

        // when
        val stream = openChat(setup, CHAT)

        // then 모델이 응답 헤더도 보내기 전에 keep-alive 가 먼저 온다
        val lines = (1..3).map { stream.nextRawNonBlank() }
        lines.forEach { it shouldContain ":keep-alive" }
        answer.countDown()
        stream.readAll().map { it.name } shouldBe listOf("done")
        stream.close()
    }

    @Test
    fun `스트림 도중 DB 연결을 쥐지 않는다`() {
        // given
        val setup = eksCluster()
        val answer = CountDownLatch(1)
        val answering = CountDownLatch(1)
        api.chat = { exchange ->
            exchange.startSse()
            answering.countDown()
            answer.await(5, TimeUnit.SECONDS)
            exchange.sendData("[DONE]")
            exchange.close()
        }

        // when
        val stream = openChat(setup, CHAT)
        answering.await(5, TimeUnit.SECONDS) shouldBe true
        Thread.sleep(300)

        // then
        dataSource.unwrap(HikariDataSource::class.java).hikariPoolMXBean.activeConnections shouldBe 0
        answer.countDown()
        stream.use { it.readAll() }
    }

    @Test
    fun `브라우저가 끊으면 상위 연결도 끊고 잠금을 풀어 다시 대화할 수 있다`(output: CapturedOutput) {
        // given
        val setup = eksCluster()
        val upstreamClosed = CountDownLatch(1)
        val answering = CountDownLatch(1)
        api.chat = { exchange ->
            exchange.startSse()
            answering.countDown()
            exchange.sendUntilClosed(upstreamClosed)
        }

        // when
        val stream = openChat(setup, CHAT)
        answering.await(5, TimeUnit.SECONDS) shouldBe true
        stream.nextEvent().name shouldBe "delta"
        stream.close()

        // then
        upstreamClosed.await(5, TimeUnit.SECONDS) shouldBe true
        // 잠금 만료(2분)를 기다리지 않고 바로 풀린다
        eventually { redis.keys("model-chat:*").shouldBeEmpty() }
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendData("[DONE]")
            exchange.close()
        }
        openChat(setup, CHAT).use { it.status shouldBe 200 }
        eventually { output.all shouldContain "outcome=aborted" }
        output.all shouldNotContain "잠금을 풀지 못했습니다"
        output.all.lines().filter { " ERROR " in it }.shouldBeEmpty()
    }

    @Test
    fun `모델이 말이 없을 때 브라우저가 끊어도 keep-alive 쓰기로 알아채고 상위 연결을 끊는다`() {
        // given
        val setup = eksCluster()
        val upstreamClosed = CountDownLatch(1)
        val answering = CountDownLatch(1)
        api.chat = { exchange ->
            exchange.startSse()
            answering.countDown()
            exchange.keepOpenUntilClosed(upstreamClosed)
        }

        // when
        val stream = openChat(setup, CHAT)
        answering.await(5, TimeUnit.SECONDS) shouldBe true
        stream.close()

        // then
        upstreamClosed.await(5, TimeUnit.SECONDS) shouldBe true
        eventually { redis.keys("model-chat:*").shouldBeEmpty() }
    }

    @Test
    fun `같은 사용자의 두 번째 대화는 MODEL_CHAT_BUSY 고 다른 사용자는 된다`() {
        // given
        val setup = eksCluster()
        val other = eksCluster()
        val finish = CountDownLatch(1)
        val answering = CountDownLatch(1)
        api.chat = { exchange ->
            exchange.startSse()
            answering.countDown()
            finish.await(5, TimeUnit.SECONDS)
            exchange.sendData("[DONE]")
            exchange.close()
        }
        val firstStream = openChat(setup, CHAT)
        answering.await(5, TimeUnit.SECONDS) shouldBe true

        // when
        val second = openChat(setup, CHAT)
        val otherStream = openChat(other, CHAT)

        // then
        second.status shouldBe 429
        second.contentType shouldContain "application/json"
        objectMapper.readTree(second.readBody()).path("code").asString() shouldBe ClusterErrorCode.MODEL_CHAT_BUSY.code
        otherStream.status shouldBe 200
        finish.countDown()
        firstStream.use { it.readAll() }
        otherStream.use { it.readAll() }
    }

    @Test
    fun `응답 전체 시간 상한이 지나면 error 이벤트로 끝내고 상위 연결을 끊는다`(output: CapturedOutput) {
        // given
        val setup = eksCluster()
        val upstreamClosed = CountDownLatch(1)
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendUntilClosed(upstreamClosed, intervalMillis = 200)
        }

        // when
        val events = openChat(setup, CHAT).use { it.readAll() }

        // then
        events.last().name shouldBe "error"
        events.last().json().path("code").asString() shouldBe ClusterErrorCode.MODEL_CHAT_TIMEOUT.code
        upstreamClosed.await(5, TimeUnit.SECONDS) shouldBe true
        eventually { output.all shouldContain "outcome=timeout" }
        eventually { redis.keys("model-chat:*").shouldBeEmpty() }
        output.all shouldNotContain "잠금을 풀지 못했습니다"
    }

    @Test
    fun `스트림 도중 로그인 토큰이 만료되어도 응답을 끝까지 받는다`(output: CapturedOutput) {
        // given
        val setup = eksCluster()
        val expiring = JwtTokenProvider(
            JwtProperties(jwtProperties.secret, Duration.ofSeconds(1), jwtProperties.refreshTokenExpiration),
        ).generateAccessToken(setup.userId)
        api.chat = { exchange ->
            exchange.startSse()
            Thread.sleep(1_500)
            exchange.sendData("""{"choices":[{"delta":{"content":"늦은 답"},"finish_reason":"stop"}]}""")
            exchange.sendData("[DONE]")
            exchange.close()
        }

        // when
        val events = openChat(setup.copy(accessToken = expiring), CHAT).use { it.readAll() }

        // then
        events.map { it.name } shouldBe listOf("delta", "done")
        // 끝난 뒤의 재디스패치까지 지나고 다른 토큰 오류가 남지 않는다
        eventually { output.all shouldContain "outcome=done" }
        Thread.sleep(200)
        output.all shouldNotContain "ExpiredTokenException"
        output.all.lines().filter { " ERROR " in it }.shouldBeEmpty()

        // 만료된 토큰으로 새 요청을 시작하지는 못한다
        openChat(setup.copy(accessToken = expiring), CHAT).use { it.status shouldBe 401 }
    }

    @ParameterizedTest
    @CsvSource(
        "401, GPU_SERVING_DENIED",
        "403, GPU_SERVING_DENIED",
        "404, MODEL_NOT_READY",
        "503, MODEL_NOT_READY",
        "400, MODEL_CHAT_REJECTED",
        "429, MODEL_CHAT_BUSY",
        "500, GPU_CLUSTER_API_FAILED",
    )
    fun `대화 요청을 상위가 거절하면 error 이벤트로 알리고 응답 본문을 옮기지 않는다`(status: Int, code: String) {
        // given
        val setup = eksCluster()
        api.chat = { exchange -> exchange.answer(status, CANARY) }

        // when
        val stream = openChat(setup, CHAT)
        val body = stream.readBody()

        // then
        stream.status shouldBe 200
        body shouldNotContain CANARY
        body shouldContain "event:error"
        body shouldContain code
    }

    @Test
    fun `DONE 도 끝난 이유도 없이 응답이 끝나면 잘린 것으로 보고 error 이벤트로 끝난다`() {
        // given
        val setup = eksCluster()
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendData("""{"choices":[{"delta":{"content":"앞부분"}}]}""")
            exchange.close()
        }

        // when
        val events = openChat(setup, CHAT).use { it.readAll() }

        // then
        events.map { it.name } shouldBe listOf("delta", "error")
        events.last().json().path("code").asString() shouldBe ClusterErrorCode.GPU_CLUSTER_API_FAILED.code
    }

    @Test
    fun `상위 연결이 도중에 끊기면 done 없이 error 이벤트로 끝난다`() {
        // given
        val setup = eksCluster()
        api.chat = { exchange ->
            // 약속한 길이를 다 보내지 않고 연결을 닫는다
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 100_000)
            exchange.sendData("""{"choices":[{"delta":{"content":"앞부분"}}]}""")
            exchange.close()
        }

        // when
        val events = openChat(setup, CHAT).use { it.readAll() }

        // then
        events.map { it.name } shouldBe listOf("delta", "error")
        events.last().json().path("code").asString() shouldBe ClusterErrorCode.GPU_CLUSTER_API_FAILED.code
    }

    @Test
    fun `대화가 어떻게 끝나도 상위 연결의 스레드가 남지 않는다`() {
        // given
        val setup = eksCluster()
        val before = selectorThreads()

        // 정상 종료
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendData("[DONE]")
            exchange.close()
        }
        openChat(setup, CHAT).use { it.readAll() }
        // 상위 거절
        api.chat = { exchange -> exchange.answer(500, "") }
        openChat(setup, CHAT).use { it.readAll() }
        // 브라우저 끊김
        val upstreamClosed = CountDownLatch(1)
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendUntilClosed(upstreamClosed)
        }
        eventually { redis.keys("model-chat:*").shouldBeEmpty() }
        val aborted = openChat(setup, CHAT)
        aborted.nextEvent()
        aborted.close()
        upstreamClosed.await(5, TimeUnit.SECONDS) shouldBe true

        // when & then
        eventually { (selectorThreads() <= before) shouldBe true }
    }

    private fun selectorThreads(): Int = Thread.getAllStackTraces().keys.count { "SelectorManager" in it.name }

    @Test
    fun `대화 시작을 거절하면 일반 JSON 오류다`() {
        // given
        val setup = eksCluster()
        api.servedModelPhase = "Loading"

        // when
        val stream = openChat(setup, CHAT)

        // then
        stream.status shouldBe 409
        stream.contentType shouldContain "application/json"
        objectMapper.readTree(stream.readBody()).path("code").asString() shouldBe ClusterErrorCode.MODEL_NOT_READY.code
        api.chatBodies.shouldBeEmpty()
    }

    @Test
    fun `메시지 내용은 어떤 흐름에서도 로그와 예외와 DB 와 Redis 에 남지 않는다`(output: CapturedOutput) {
        // given
        val setup = eksCluster()
        val chat = """{"messages":[{"role":"user","content":"$CANARY"}]}"""

        // 끝까지 받는 흐름. 모델의 답에도 같은 문구를 넣는다
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendData("""{"choices":[{"delta":{"content":"$CANARY","reasoning":"$CANARY"},"finish_reason":"stop"}],"usage":{"completion_tokens":1}}""")
            exchange.sendData("[DONE]")
            exchange.close()
        }
        val answered = openChat(setup, chat).use { it.readBody() }

        // 상위가 원문을 돌려주며 거절하는 흐름
        api.chat = { exchange -> exchange.answer(500, """{"error":"$CANARY"}""") }
        openChat(setup, chat).use { it.readBody() }

        // 상위가 원문이 든 오류와 깨진 줄을 스트림으로 보내는 흐름
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendData("""{"error":{"message":"$CANARY"}}""")
            exchange.close()
        }
        openChat(setup, chat).use { it.readBody() }
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendData("{$CANARY")
            exchange.close()
        }
        openChat(setup, chat).use { it.readBody() }

        // 브라우저가 끊는 흐름
        val upstreamClosed = CountDownLatch(1)
        api.chat = { exchange ->
            exchange.startSse()
            exchange.sendUntilClosed(upstreamClosed)
        }
        val aborted = openChat(setup, chat)
        aborted.nextEvent()
        aborted.close()
        upstreamClosed.await(5, TimeUnit.SECONDS) shouldBe true

        // 요청 형식이 틀린 흐름과 너무 큰 흐름
        eventually { openChat(setup, """{"messages":[{"role":"tool","content":"$CANARY"}]}""").use { it.status shouldBe 400 } }
        eventually { openChat(setup, """{"messages":[{"role":"user","content":"$CANARY""").use { it.status shouldBe 400 } }

        // when & then
        api.chatBodies.first() shouldContain CANARY
        answered shouldContain CANARY
        eventually { output.all shouldContain "outcome=aborted" }
        // 로그를 실제로 잡고 있는지 먼저 본다
        output.all shouldContain "GPU 조작 끝: user=${setup.userId} cluster=${setup.clusterId}"
        output.all shouldNotContain CANARY
        output.all shouldNotContain "k8s-aws-v1."

        val tables = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
            String::class.java,
        )
        (tables.size > 5) shouldBe true
        tables.forEach { table ->
            jdbc.queryForList("SELECT t::text FROM $table t", String::class.java).forEach { row -> row shouldNotContain CANARY }
        }
        val keys = redis.keys("*")
        keys.forEach { key ->
            key shouldNotContain CANARY
            if (redis.type(key)?.code() == "string") redis.opsForValue().get(key).orEmpty() shouldNotContain CANARY
        }
    }

    private fun openChat(setup: Setup, body: String): ChatStream {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/clusters/${setup.clusterId}/gpu/served-models/gpt-oss-20b/chat"))
            .header("Authorization", "Bearer ${setup.accessToken}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return ChatStream(browser.send(request, HttpResponse.BodyHandlers.ofInputStream()), objectMapper)
    }

    private fun eventually(assertion: () -> Unit) {
        val deadline = Instant.now().plusSeconds(5)
        while (true) {
            try {
                assertion()
                return
            } catch (error: AssertionError) {
                if (Instant.now() >= deadline) throw error
                Thread.sleep(100)
            }
        }
    }

    private fun eksCluster(): Setup {
        val user = loggedInUser(UserFixture.user(email = "${UUID.randomUUID()}@example.com"))
        val teamId = savedTeam(user.user.id to TeamRole.ADMIN)
        val externalId = "kukie-${UUID.randomUUID().toString().take(16)}"
        val connection = AwsConnection(teamId = teamId, createdBy = user.user.id, externalId = externalId)
        connection.connect(ACCOUNT_ID, LocalDateTime.now(), null, AccessLevel.FULL, false, null)
        awsConnectionRepository.save(connection)
        val credential = EksCredential(ACCOUNT_ID, externalId, DEFAULT_REGION, DEFAULT_CLUSTER_NAME)
        val cluster = clusterRepository.save(
            ClusterFixture.cluster(
                registeredBy = user.user.id,
                teamId = teamId,
                credential = credentialCipher.encrypt(credential.toMap()),
                provider = ClusterProvider.EKS,
                accessLevel = AccessLevel.FULL,
            ),
        )
        return Setup(cluster.id, user.user.id, user.accessToken)
    }

    private data class Setup(
        val clusterId: UUID,
        val userId: UUID,
        val accessToken: String,
    )

    private class SseEvent(
        val name: String?,
        val data: String?,
        private val objectMapper: ObjectMapper,
    ) {
        fun json() = objectMapper.readTree(data.shouldNotBeNull())
    }

    /** 브라우저 쪽. SSE 를 바이트 그대로 읽어 몰려서 오는지 볼 수 있게 한다. */
    private class ChatStream(
        private val response: HttpResponse<java.io.InputStream>,
        private val objectMapper: ObjectMapper,
    ) : AutoCloseable {
        private val reader = response.body().bufferedReader()

        val status: Int get() = response.statusCode()
        val headers get() = response.headers()
        val contentType: String get() = headers.firstValue("Content-Type").orElse("")

        fun readBody(): String = reader.readText()

        fun nextRawNonBlank(): String {
            while (true) {
                val line = reader.readLine() ?: return ""
                if (line.isNotBlank()) return line
            }
        }

        /** 다음 이름 있는 이벤트. keep-alive 는 건너뛴다. 끝나면 name 이 null 이다. */
        fun nextEvent(): SseEvent {
            var name: String? = null
            var data: String? = null
            while (true) {
                val line = reader.readLine() ?: return SseEvent(name, data, objectMapper)
                when {
                    line.startsWith("event:") -> name = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> data = line.removePrefix("data:").trim()
                    line.isEmpty() && name != null -> return SseEvent(name, data, objectMapper)
                }
            }
        }

        fun readAll(): List<SseEvent> = generateSequence { nextEvent().takeIf { it.name != null } }.toList()

        override fun close() {
            response.body().close()
        }
    }

    /** 로컬 가짜 API 서버. ServedModel 읽기와 Service 프록시를 흉내 낸다. */
    private class FakeKubernetes {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newCachedThreadPool()

        val url: String get() = "http://127.0.0.1:${server.address.port}"
        val chatBodies = java.util.concurrent.CopyOnWriteArrayList<String>()
        val chatPaths = java.util.concurrent.CopyOnWriteArrayList<String>()
        val chatAuthorizations = java.util.concurrent.CopyOnWriteArrayList<String?>()

        @Volatile
        var servedModelPhase = "Ready"

        @Volatile
        var chat: (HttpExchange) -> Unit = { it.answer(500, "not scripted") }

        fun start() {
            server.executor = executor
            server.createContext("/apis/serving.kukie.io/") { exchange ->
                exchange.answer(
                    200,
                    """
                    {"metadata":{"resourceVersion":"7"},"spec":{"source":{"huggingface":"openai/gpt-oss-20b"}},
                     "status":{"phase":"$servedModelPhase"}}
                    """.trimIndent(),
                )
            }
            server.createContext("/api/v1/") { exchange ->
                chatBodies += exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
                chatPaths += exchange.requestURI.toString()
                chatAuthorizations += exchange.requestHeaders.getFirst("Authorization")
                try {
                    chat(exchange)
                } catch (_: IOException) {
                    // 끊긴 연결에 쓴 것이다
                }
            }
            server.start()
        }

        fun stop() {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private class StubSts : StsClient {
        override fun assumeRole(request: AssumeRoleRequest): AssumeRoleResponse =
            AssumeRoleResponse.builder()
                .credentials(
                    Credentials.builder()
                        .accessKeyId("AKIDEXAMPLE")
                        .secretAccessKey("secret-key")
                        .sessionToken("session")
                        .expiration(Instant.now().plus(1, ChronoUnit.HOURS))
                        .build(),
                )
                .build()

        override fun serviceName(): String = "sts"

        override fun close() = Unit
    }

    companion object {
        private const val ACCOUNT_ID = "123456789012"
        private const val CHAT = """{"messages":[{"role":"user","content":"안녕"}]}"""
        private const val CANARY = "CANARY-사용자-비밀-문구-7f3a"
    }
}

private fun HttpExchange.answer(status: Int, body: String) {
    val bytes = body.toByteArray()
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.write(bytes)
    close()
}

private fun HttpExchange.startSse() {
    responseHeaders.add("Content-Type", "text/event-stream")
    sendResponseHeaders(200, 0)
    responseBody.flush()
}

private fun HttpExchange.sendData(data: String) {
    responseBody.write("data: $data\n\n".toByteArray())
    responseBody.flush()
}

/** 끊길 때까지 답을 보낸다. 끊기면 쓰기가 실패한다. */
private fun HttpExchange.sendUntilClosed(closed: CountDownLatch, intervalMillis: Long = 50) {
    val stop = Instant.now().plusSeconds(20)
    try {
        while (Instant.now() < stop) {
            sendData("""{"choices":[{"delta":{"content":"토막 "}}]}""")
            Thread.sleep(intervalMillis)
        }
    } catch (_: IOException) {
        closed.countDown()
    } finally {
        close()
    }
}

/** 아무것도 보내지 않고 기다린다. 끊긴 것은 빈 주석을 써서 확인한다 — 모델이 생성을 멈추는 지점과 같다. */
private fun HttpExchange.keepOpenUntilClosed(closed: CountDownLatch) {
    val stop = Instant.now().plusSeconds(20)
    try {
        while (Instant.now() < stop) {
            responseBody.write(":\n".toByteArray())
            responseBody.flush()
            Thread.sleep(50)
        }
    } catch (_: IOException) {
        closed.countDown()
    } finally {
        close()
    }
}
