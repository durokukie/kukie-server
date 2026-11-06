package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.`in`.ConfigureServingEndpointCommand
import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import com.duro.kukie.serving.application.port.out.*
import com.duro.kukie.serving.domain.*
import com.duro.kukie.support.*
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.servlet.AsyncEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.mock.web.MockAsyncContext
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import(RelayFakeConfig::class)
@org.springframework.test.context.TestPropertySource(properties = [
    "serving.relay.allowed-hosts=a.example.com,b.example.com",
    "serving.relay.max-connections=2", "serving.relay.max-connections-per-key=1", "serving.relay.total-seconds=2",
])
class ServingRelayIntegrationTest : IntegrationTest() {
    @Autowired private lateinit var usageJournal: ServingUsageRepository
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var endpoints: ServingEndpointRepository
    @Autowired private lateinit var configure: ConfigureServingEndpointService
    @Autowired private lateinit var issue: IssueServingApiKeyService
    @Autowired private lateinit var revoke: RevokeMyServingApiKeyService
    @Autowired private lateinit var cipher: CredentialCipher
    @Autowired private lateinit var counter: FakeCounterTransport
    @Autowired private lateinit var completionProbe: CompletionInterruptionProbe
    @Autowired private lateinit var observations: RelayObservations

    @Test
    fun `키로 회사별 카운터를 선택하고 다른 인증정보는 전달하지 않는다`() {
        // given
        val a = setup("a")
        val b = setup("b", a.user)
        val body = """{"model":"shared","messages":[{"role":"user","content":"비밀 질문"}]}"""
        // when
        val ra = chat(a.token, body)
        val rb = chat(b.token, body)
        observations.next().outcome shouldBe "completed"
        observations.next().outcome shouldBe "completed"
        // then
        ra.response.status shouldBe 200
        rb.response.status shouldBe 200
        counter.calls.map { it.target.baseUrl } shouldBe listOf("https://a.example.com/v1", "https://b.example.com/v1")
        counter.calls.map { it.target.serviceToken } shouldBe listOf("counter-a", "counter-b")
        counter.calls.forEach { it.body!!.toString(Charsets.UTF_8) shouldBe body }
        val stored = endpoints.findByClusterId(a.clusterId)!!
        stored.encryptedToken.contains("counter-a") shouldBe false
        cipher.decrypt(stored.encryptedToken)["token"] shouldBe "counter-a"
    }

    @Test
    fun `SSE 바이트를 그대로 전달하고 본문 없는 토큰 이벤트만 발행한다`() {
        // given
        val s = setup("a")
        val sse = ": keepalive\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"안녕\"}}]}\n\ndata: {\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":7}}\n\ndata: [DONE]\n\n"
        counter.answer = { CounterResponse(200, "text/event-stream; charset=utf-8", ByteArrayInputStream(sse.toByteArray())) }
        // when
        val result = chat(s.token, """{"model":"shared","stream":true,"messages":[]}""")
        val event = observations.next()
        // then
        result.response.contentAsByteArray.toList() shouldBe sse.toByteArray().toList()
        result.response.contentType shouldBe "text/event-stream"
        event.inputTokens shouldBe 3
        event.outputTokens shouldBe 7
        event.usageMissing shouldBe false
        event.outcome shouldBe "completed"
        event.toString().contains("안녕") shouldBe false
        event.identity.clusterId shouldBe s.clusterId
        val stored = usageJournal.findById(event.requestId).get()
        stored.outcome shouldBe "COMPLETED"
        stored.inputTokens shouldBe 3
        stored.outputTokens shouldBe 7
    }

    @Test
    fun `모델 목록은 등록된 모델만 반환하고 미허용 모델 요청은 차단한다`() {
        // given
        val s = setup("a")
        counter.answer = { CounterResponse(200, "application/json", ByteArrayInputStream("""{"data":[{"id":"shared","secret":"internal"},{"id":"hidden"}]}""".toByteArray())) }
        // when
        val result = mockMvc.get("/serving/v1/models") { authorization(s.token) }.andReturn()
        observations.next()
        // then
        result.response.contentAsString.contains("hidden") shouldBe false
        result.response.contentAsString.contains("internal") shouldBe false
        result.response.contentAsString.contains("shared") shouldBe true
        chat(s.token, """{"model":"hidden","messages":[]}""").response.status shouldBe 403
        counter.calls.size shouldBe 1
    }

    @Test
    fun `로그인 쿠키와 JWT는 키 인증을 대체하지 않고 폐기 키도 거절한다`() {
        // given
        val s = setup("a")
        // when then
        mockMvc.get("/serving/v1/models").andExpect { status { isUnauthorized() } }
        mockMvc.get("/serving/v1/models") { authorization(s.user.accessToken) }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/serving/v1/models") { cookie(jakarta.servlet.http.Cookie("kukie_access", s.user.accessToken)) }.andExpect { status { isUnauthorized() } }
        revoke(s.accessId, s.user.user.id, s.keyId)
        chat(s.token, """{"model":"shared"}""").response.status shouldBe 401
        counter.calls.size shouldBe 0
    }

    @Test
    fun `요청 경로와 중복 모델 필드 및 본문 크기를 검증한다`() {
        // given
        val s = setup("a")
        // when then
        for (body in listOf("[]", "{", """{"model":"shared","stream":"yes"}""", """{"model":"shared","model":"hidden"}""")) {
            chat(s.token, body).response.status shouldBe 400
        }
        chat(s.token, "x".repeat(1024 * 1024 + 1)).response.status shouldBe 413
        mockMvc.get("/serving/v1/models?url=https://b.example.com") { authorization(s.token) }.andExpect { status { isBadRequest() } }
        counter.calls.size shouldBe 0
    }

    @Test
    fun `상위 오류와 리다이렉트 본문은 노출하지 않고 상태를 구분한다`() {
        // given
        val s = setup("a")
        // when then
        for ((upstream, expected) in listOf(400 to 400, 401 to 502, 403 to 502, 429 to 429, 404 to 503, 503 to 503, 302 to 502, 500 to 502)) {
            counter.answer = { CounterResponse(upstream, "application/json", ByteArrayInputStream("counter-secret internal-url".toByteArray())) }
            val result = chat(s.token, """{"model":"shared"}""")
            observations.next()
            result.response.status shouldBe expected
            result.response.contentAsString.contains("counter-secret") shouldBe false
        }
    }

    @Test
    fun `키별 동시 상한과 취소 후 슬롯 반환을 검증한다`() {
        // given
        val s = setup("a")
        val waiting = BlockingCounterBody()
        counter.answer = { CounterResponse(200, "text/event-stream", waiting) }
        val first = chat(s.token, """{"model":"shared","stream":true}""")
        // when
        chat(s.token, """{"model":"shared","stream":true}""").response.status shouldBe 429
        val context = first.request.asyncContext as MockAsyncContext
        context.listeners.forEach { it.onError(AsyncEvent(context, IOException("gone"))) }
        val event = observations.next()
        // then
        counter.calls.first().cancelled.get() shouldBe true
        event.outcome shouldBe "cancelled"
        counter.answer = { jsonResponse() }
        chat(s.token, """{"model":"shared"}""")
        observations.next().outcome shouldBe "completed"
    }

    @Test
    fun `전체 시간 제한은 카운터를 취소하고 슬롯을 반환한다`() {
        // given
        val s = setup("a")
        counter.answer = { CounterResponse(200, "text/event-stream", BlockingCounterBody()) }
        // when
        val result = chat(s.token, """{"model":"shared","stream":true}""")
        val event = observations.next()
        // then
        result.response.status shouldBe 504
        event.outcome shouldBe "timeout"
        counter.calls.first().cancelled.get() shouldBe true
        counter.answer = { jsonResponse() }
        chat(s.token, """{"model":"shared"}""")
        observations.next().outcome shouldBe "completed"
    }

    @Test
    fun `유휴 시간 초과와 끊긴 SSE의 사용량 누락을 구분한다`() {
        // given
        val s = setup("a")
        counter.answer = { throw SocketTimeoutException("contains-secret") }
        // when then
        val timeout = chat(s.token, """{"model":"shared"}""")
        observations.next().outcome shouldBe "timeout"
        timeout.response.status shouldBe 504
        timeout.response.contentAsString.contains("contains-secret") shouldBe false
        counter.answer = { CounterResponse(200, "text/event-stream", ByteArrayInputStream("data: {}\n\n".toByteArray())) }
        chat(s.token, """{"model":"shared","stream":true}""")
        val event = observations.next()
        event.outcome shouldBe "incomplete"
        event.usageMissing shouldBe true
    }

    @Test
    fun `대상 등록 조회 삭제는 관리자만 가능하고 암호화 토큰은 응답하지 않는다`() {
        // given
        val s = setup("a")
        val other = setup("b")
        // when then
        mockMvc.get("/clusters/${s.clusterId}/serving/endpoint") { authorization(s.admin.accessToken) }.andExpect {
            status { isOk() }; jsonPath("$.serviceToken") { doesNotExist() }; jsonPath("$.encryptedToken") { doesNotExist() }
        }
        mockMvc.get("/clusters/${s.clusterId}/serving/endpoint") { authorization(other.admin.accessToken) }.andExpect { status { isNotFound() } }
        mockMvc.put("/clusters/${s.clusterId}/serving/endpoint") {
            authorization(s.user.accessToken); contentType = MediaType.APPLICATION_JSON
            content = mapOf("baseUrl" to "https://a.example.com", "serviceToken" to "secret", "models" to listOf("shared")).toJson()
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `취소와 완료 콜백이 반복돼도 원장 완료 기록을 인터럽트하지 않는다`() {
        // given
        val s = setup("a")
        completionProbe.armed = true
        counter.answer = { CounterResponse(200, "text/event-stream", BlockingCounterBody()) }
        val result = chat(s.token, """{"model":"shared","stream":true}""")
        val context = result.request.asyncContext as MockAsyncContext
        // when
        try {
            context.listeners.toList().forEach { it.onError(AsyncEvent(context, IOException("gone"))) }
            completionProbe.entered.await(5, TimeUnit.SECONDS) shouldBe true
            context.listeners.toList().forEach { it.onComplete(AsyncEvent(context)) }
        } finally { completionProbe.release.countDown() }
        val event = observations.next()
        // then
        completionProbe.interrupted.get() shouldBe false
        usageJournal.findById(event.requestId).get().outcome shouldBe "CANCELLED"
    }

    private fun chat(token: String, body: String) = mockMvc.post("/serving/v1/chat/completions") {
        authorization(token); contentType = MediaType.APPLICATION_JSON; content = body
    }.andReturn()

    private fun setup(label: String, client: LoggedInUser? = null): Setup {
        val admin = loggedInUser(UserFixture.user(email = "$label-admin@example.com"))
        val user = client ?: loggedInUser(UserFixture.user(email = "$label-user@example.com"))
        val teamId = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, teamId, name = label))
        val access = accesses.save(ServingAccess(cluster.id, user.user.email, admin.user.id, utcNow().plusDays(7)).also { it.accept(user.user.id, user.user.email, utcNow()) })
        configure(ConfigureServingEndpointCommand(cluster.id, admin.user.id, "https://$label.example.com/v1", "counter-$label", listOf("shared")))
        val key = issue(IssueServingApiKeyCommand(access.id, user.user.id, "개발", 90))
        return Setup(admin, user, cluster.id, access.id, key.key.id, key.token)
    }
    private class Setup(val admin: LoggedInUser, val user: LoggedInUser, val clusterId: UUID, val accessId: UUID, val keyId: UUID, val token: String)
}

@TestConfiguration
class RelayFakeConfig {
    @Bean @Primary fun fakeCounterTransport() = FakeCounterTransport()
    @Bean fun relayObservations() = RelayObservations()
    @Bean fun completionProbe() = CompletionInterruptionProbe()
}

class RelayObservations : Resettable {
    private val queue = LinkedBlockingQueue<ServingUsageObservation>()
    @EventListener fun onObservation(event: ServingUsageObservation) { queue.add(event) }
    fun next(): ServingUsageObservation = checkNotNull(queue.poll(8, TimeUnit.SECONDS)) { "중계 완료 이벤트가 없습니다" }
    override fun clear() { queue.clear() }
}

class FakeCounterTransport : CounterTransport, Resettable {
    val calls = CopyOnWriteArrayList<FakeCounterCall>()
    var answer: (CounterTarget) -> CounterResponse = { jsonResponse() }
    override fun create(target: CounterTarget, path: String, body: ByteArray?): CounterExchange = FakeCounterCall(target, path, body, answer).also(calls::add)
    override fun clear() { calls.forEach { it.cancel() }; calls.clear(); answer = { jsonResponse() } }
}

class FakeCounterCall(val target: CounterTarget, val path: String, val body: ByteArray?, private val answer: (CounterTarget) -> CounterResponse) : CounterExchange {
    val cancelled = AtomicBoolean()
    @Volatile private var input: InputStream? = null
    override fun execute(consume: (CounterResponse) -> Unit) {
        val response = answer(target)
        input = response.body
        if (cancelled.get()) { response.body.close(); throw IOException() }
        response.body.use { consume(response) }
    }
    override fun cancel() { cancelled.set(true); input?.close() }
}

class BlockingCounterBody : InputStream() {
    private val closed = java.util.concurrent.CountDownLatch(1)
    override fun read(): Int { closed.await(10, TimeUnit.SECONDS); throw IOException() }
    override fun close() { closed.countDown() }
}

private fun jsonResponse() = CounterResponse(200, "application/json", ByteArrayInputStream("""{"choices":[],"usage":{"prompt_tokens":2,"completion_tokens":4}}""".toByteArray()))

class CompletionInterruptionProbe : Resettable {
    @Volatile var armed = false
    var entered = java.util.concurrent.CountDownLatch(1)
        private set
    var release = java.util.concurrent.CountDownLatch(1)
        private set
    val interrupted = AtomicBoolean()
    @EventListener
    @org.springframework.core.annotation.Order(-1)
    fun onObservation(event: ServingUsageObservation) {
        if (!armed || event.outcome != "cancelled") return
        entered.countDown()
        try { release.await(5, TimeUnit.SECONDS) }
        catch (_: InterruptedException) { interrupted.set(true) }
    }
    override fun clear() {
        release.countDown()
        armed = false
        entered = java.util.concurrent.CountDownLatch(1)
        release = java.util.concurrent.CountDownLatch(1)
        interrupted.set(false)
    }
}
