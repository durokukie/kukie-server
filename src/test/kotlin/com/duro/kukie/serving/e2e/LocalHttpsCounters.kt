package com.duro.kukie.serving.e2e

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.application.port.out.CounterTarget
import com.duro.kukie.serving.application.port.out.CounterTransport
import com.duro.kukie.serving.infrastructure.HttpsCounterTransport
import com.duro.kukie.serving.infrastructure.ServingDestinationPolicy
import com.duro.kukie.serving.infrastructure.counterHttpClient
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.mockk.every
import io.mockk.mockk
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.springframework.beans.factory.DisposableBean
import tools.jackson.databind.json.JsonMapper
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class LocalHttpsCounters : CounterTransport, DisposableBean {
    class Seen(val provider: String, val authorization: String?, val cookie: String?)
    val calls = CopyOnWriteArrayList<Seen>()
    private val threads = Executors.newVirtualThreadPerTaskExecutor()
    private val mapper = JsonMapper.builder().build()
    private val context: SSLContext
    private val a: HttpsServer
    private val b: HttpsServer
    private val transport: HttpsCounterTransport
    init {
        val store = KeyStore.getInstance("PKCS12").apply { LocalHttpsCounters::class.java.getResourceAsStream("/serving/counter-test.p12")!!.use { load(it, "test-only".toCharArray()) } }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test-only".toCharArray()) }
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        context = SSLContext.getInstance("TLS").apply { init(km.keyManagers, tm.trustManagers, null) }
        val policy = mockk<ServingDestinationPolicy>()
        every { policy.resolve("counter.test") } returns arrayOf(InetAddress.getLoopbackAddress())
        transport = HttpsCounterTransport(counterHttpClient(policy, ServingRelayProperties(idleSeconds = 1), ClientTlsStrategyBuilder.create().setSslContext(context).buildClassic()))
        a = server("a"); b = server("b")
    }
    override fun create(target: CounterTarget, path: String, body: ByteArray?) = transport.create(
        // 본 배포의 HTTPS/443 정책은 유지하고 테스트 전송 계층에서만 로컬 TLS 포트로 매핑한다.
        CounterTarget("https://counter.test:${when (target.baseUrl) { "https://a.example.com/v1" -> a.address.port; "https://b.example.com/v1" -> b.address.port; else -> error("unknown test target") }}/v1", target.serviceToken), path, body,
    )
    private fun server(label: String): HttpsServer = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        httpsConfigurator = HttpsConfigurator(context)
        executor = threads
        createContext("/v1/") { exchange ->
            calls.add(Seen(label, exchange.requestHeaders.getFirst("Authorization"), exchange.requestHeaders.getFirst("Cookie")))
            try {
                val request = exchange.requestBody.readAllBytes()
                val input = if (request.isEmpty()) null else mapper.readTree(request)
                val mode = input?.get("fixture_mode")?.asString() ?: "normal"
                val stream = input?.get("stream")?.asBoolean() ?: false
                var status = if (exchange.requestHeaders.getFirst("Authorization") == "Bearer service-$label") 200 else 401
                if (mode == "rate") status = 429
                if (mode == "unavailable") status = 503
                if (mode == "redirect") { status = 302; exchange.responseHeaders.add("Location", "https://127.0.0.1/private") }
                if (mode == "drop") { exchange.close(); return@createContext }
                val result = when {
                    status != 200 -> "private-upstream-error"
                    exchange.requestURI.path.endsWith("/models") -> """{"data":[{"id":"shared"},{"id":"hidden"}]}"""
                    stream -> if (mode == "truncate") "data: {\"choices\":[]}\n\n" else sse(label)
                    else -> """{"choices":[{"message":{"content":"reply-$label-canary"}}],"usage":{"prompt_tokens":11,"completion_tokens":22}}"""
                }
                exchange.responseHeaders.add("Content-Type", if (stream && status == 200) "text/event-stream" else "application/json")
                exchange.sendResponseHeaders(status, 0)
                when (mode) {
                    "idle" -> { exchange.responseBody.flush(); Thread.sleep(2500) }
                    "hold" -> repeat(100) { exchange.responseBody.write(": beat\n\n".toByteArray()); exchange.responseBody.flush(); Thread.sleep(50) }
                    else -> exchange.responseBody.write(result.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) { }
            finally { runCatching { exchange.close() } }
        }
        start()
    }
    override fun destroy() { a.stop(0); b.stop(0); threads.shutdownNow(); transport.destroy() }
    companion object {
        fun sse(label: String) = ": beat\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"reply-$label-canary\"}}]}\n\ndata: {\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":7}}\n\ndata: [DONE]\n\n"
    }
}
