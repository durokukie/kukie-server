package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.application.port.out.CounterTarget
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class HttpsCounterTransportTest {
    @Test
    fun `검증한 DNS 주소로 TLS 연결하고 리다이렉트 쿠키와 재시도를 사용하지 않는다`() {
        // given
        withCounter { server, context ->
            var requests = 0
            server.createContext("/v1/chat/completions") { exchange ->
                requests++
                exchange.requestHeaders.getFirst("Authorization") shouldBe "Bearer counter-service-only"
                exchange.requestHeaders.getFirst("Cookie") shouldBe null
                exchange.requestBody.readAllBytes().toString(Charsets.UTF_8) shouldBe "{}"
                exchange.responseHeaders.add("Location", "https://127.0.0.1/secret")
                exchange.responseHeaders.add("Set-Cookie", "secret=value")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            val policy = mockk<ServingDestinationPolicy>()
            every { policy.resolve("counter.test") } returns arrayOf(InetAddress.getLoopbackAddress())
            val client = counterHttpClient(policy, ServingRelayProperties(), ClientTlsStrategyBuilder.create().setSslContext(context).buildClassic())
            val transport = HttpsCounterTransport(client)
            // when then
            try {
                val call = transport.create(CounterTarget("https://counter.test:${server.address.port}/v1", "counter-service-only"), "/chat/completions", "{}".toByteArray())
                call.execute { it.status shouldBe 302 }
                requests shouldBe 1
                verify(exactly = 1) { policy.resolve("counter.test") }
                verify(exactly = 0) { policy.resolve("127.0.0.1") }
            } finally { transport.destroy() }
        }
    }

    @Test
    fun `실제 TLS 소켓의 유휴 시간 초과와 취소가 읽기를 종료한다`() {
        // given
        withCounter { server, context ->
            val entered = CountDownLatch(2)
            val release = CountDownLatch(1)
            server.createContext("/v1/chat/completions") { exchange ->
                exchange.requestBody.readAllBytes()
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write(": ready\n\n".toByteArray())
                exchange.responseBody.flush()
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                runCatching { exchange.close() }
            }
            val policy = mockk<ServingDestinationPolicy>()
            every { policy.resolve("counter.test") } returns arrayOf(InetAddress.getLoopbackAddress())
            val transport = HttpsCounterTransport(counterHttpClient(policy, ServingRelayProperties(idleSeconds = 1), ClientTlsStrategyBuilder.create().setSslContext(context).buildClassic()))
            val workers = Executors.newVirtualThreadPerTaskExecutor()
            try {
                val target = CounterTarget("https://counter.test:${server.address.port}/v1", "test-token")
                // when then
                shouldThrow<IOException> { transport.create(target, "/chat/completions", "{}".toByteArray()).execute { it.body.readAllBytes() } }
                val call = transport.create(target, "/chat/completions", "{}".toByteArray())
                val future = workers.submit<Boolean> {
                    try { call.execute { it.body.readAllBytes() }; false } catch (_: IOException) { true }
                }
                entered.await(3, TimeUnit.SECONDS) shouldBe true
                call.cancel()
                future.get(3, TimeUnit.SECONDS) shouldBe true
            } finally { release.countDown(); workers.shutdownNow(); transport.destroy() }
        }
    }

    @Test
    fun `거절한 상위 오류 본문이 끝나지 않아도 추가로 읽지 않고 종료한다`() {
        // given
        withCounter { server, context ->
            val release = CountDownLatch(1)
            server.createContext("/v1/models") { exchange ->
                exchange.sendResponseHeaders(503, 0)
                exchange.responseBody.flush()
                release.await(5, TimeUnit.SECONDS)
                runCatching { exchange.close() }
            }
            val policy = mockk<ServingDestinationPolicy>()
            every { policy.resolve("counter.test") } returns arrayOf(InetAddress.getLoopbackAddress())
            val transport = HttpsCounterTransport(counterHttpClient(policy, ServingRelayProperties(), ClientTlsStrategyBuilder.create().setSslContext(context).buildClassic()))
            val workers = Executors.newVirtualThreadPerTaskExecutor()
            // when then
            try {
                val future = workers.submit<Int> {
                    var status = 0
                    transport.create(CounterTarget("https://counter.test:${server.address.port}/v1", "test-token"), "/models", null).execute { status = it.status }
                    status
                }
                future.get(3, TimeUnit.SECONDS) shouldBe 503
            } finally { release.countDown(); workers.shutdownNow(); transport.destroy() }
        }
    }

    @Test
    fun `TLS 인증서 이름이 다른 호스트는 거절한다`() {
        // given
        withCounter { server, context ->
            server.createContext("/v1/models") { exchange -> exchange.sendResponseHeaders(200, -1); exchange.close() }
            val policy = mockk<ServingDestinationPolicy>()
            every { policy.resolve("wrong.test") } returns arrayOf(InetAddress.getLoopbackAddress())
            val transport = HttpsCounterTransport(counterHttpClient(policy, ServingRelayProperties(), ClientTlsStrategyBuilder.create().setSslContext(context).buildClassic()))
            // when then
            try {
                shouldThrow<IOException> { transport.create(CounterTarget("https://wrong.test:${server.address.port}/v1", "test-token"), "/models", null).execute {} }
            } finally { transport.destroy() }
        }
    }

    private fun withCounter(block: (HttpsServer, SSLContext) -> Unit) {
        val store = KeyStore.getInstance("PKCS12").apply { HttpsCounterTransportTest::class.java.getResourceAsStream("/serving/counter-test.p12")!!.use { load(it, "test-only".toCharArray()) } }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test-only".toCharArray()) }
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val context = SSLContext.getInstance("TLS").apply { init(km.keyManagers, tm.trustManagers, null) }
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.httpsConfigurator = HttpsConfigurator(context)
        server.executor = executor
        server.start()
        try { block(server, context) } finally { server.stop(0); executor.shutdownNow() }
    }
}
