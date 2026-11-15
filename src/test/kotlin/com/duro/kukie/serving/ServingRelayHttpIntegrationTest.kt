package com.duro.kukie.serving

import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.IssueServingApiKeyService
import com.duro.kukie.serving.application.ServingUsageObservation
import com.duro.kukie.serving.application.port.`in`.IssueServingApiKeyCommand
import com.duro.kukie.serving.application.port.out.CounterResponse
import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingEndpoint
import com.duro.kukie.serving.domain.ServingEndpointRepository
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["serving.relay.total-seconds=3"])
@Import(RelayFakeConfig::class)
class ServingRelayHttpIntegrationTest : IntegrationTest() {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var clusters: ClusterRepository
    @Autowired private lateinit var accesses: ServingAccessRepository
    @Autowired private lateinit var endpoints: ServingEndpointRepository
    @Autowired private lateinit var cipher: CredentialCipher
    @Autowired private lateinit var issue: IssueServingApiKeyService
    @Autowired private lateinit var counter: FakeCounterTransport
    @Autowired private lateinit var observations: RelayObservations

    @Test
    fun `실제 HTTP 클라이언트가 SSE를 닫으면 상위 연결도 취소한다`() {
        // given
        val token = setup()
        val input = RepeatingSseBody()
        counter.answer = { CounterResponse(200, "text/event-stream", input) }
        // when
        HttpClient.newHttpClient().use { client ->
            val response = client.send(request(token), HttpResponse.BodyHandlers.ofInputStream())
            response.statusCode() shouldBe 200
            response.body().use { it.readNBytes(32).isNotEmpty() shouldBe true }
        }
        // then
        val event = observations.next()
        input.closed.await(3, TimeUnit.SECONDS) shouldBe true
        counter.calls.single().cancelled.get() shouldBe true
        event.outcome shouldBe "cancelled"
    }

    @Test
    fun `실제 HTTP 전체 제한은 무응답 상위를 취소하고 504로 종료한다`() {
        // given
        val token = setup()
        counter.answer = { CounterResponse(200, "text/event-stream", BlockingCounterBody()) }
        // when
        val response = HttpClient.newHttpClient().use { it.send(request(token), HttpResponse.BodyHandlers.ofString()) }
        // then
        response.statusCode() shouldBe 504
        observations.next().outcome shouldBe "timeout"
        counter.calls.single().cancelled.get() shouldBe true
    }

    private fun request(token: String) = HttpRequest.newBuilder(URI("http://localhost:$port/serving/v1/chat/completions"))
        .header("Authorization", "Bearer $token").header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""{"model":"shared","stream":true,"messages":[]}""")).build()

    private fun setup(): String {
        val admin = loggedInUser(UserFixture.user(email = "admin@example.com"))
        val user = loggedInUser(UserFixture.user(email = "user@example.com"))
        val team = savedTeam(admin.user.id to TeamRole.ADMIN)
        val cluster = clusters.save(ClusterFixture.cluster(admin.user.id, team))
        val access = accesses.save(ServingAccess(cluster.id, user.user.email, admin.user.id, utcNow().plusDays(7)).also { it.accept(user.user.id, user.user.email, utcNow()) })
        endpoints.save(ServingEndpoint(cluster.id, "https://counter.example.com/v1", cipher.encrypt(mapOf("token" to "service-only")), listOf("shared")))
        return issue(IssueServingApiKeyCommand(access.id, user.user.id, "test", 90)).token
    }
}

private class RepeatingSseBody : InputStream() {
    val closed = CountDownLatch(1)
    private val bytes = ("data: {\"choices\":[{\"delta\":{\"content\":\"" + "x".repeat(8000) + "\"}}]}\n\n").toByteArray()
    private var position = 0
    override fun read(): Int {
        if (closed.count == 0L) return -1
        return bytes[(position++ % bytes.size)].toInt() and 255
    }
    override fun close() { closed.countDown() }
}
