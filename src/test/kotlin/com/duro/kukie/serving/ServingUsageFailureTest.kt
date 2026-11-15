package com.duro.kukie.serving

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.application.port.out.*
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

class ServingUsageFailureTest {
    @Test
    fun `시작 기록이 실패하면 상위 요청을 보내지 않고 슬롯을 반환한다`() {
        // given
        val identity = AuthenticatedServingKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val transport = mockk<CounterTransport>()
        val exchange = mockk<CounterExchange>(relaxed = true)
        every { transport.create(any(), any(), any()) } returns exchange
        val begin = mockk<BeginServingUsageService>()
        every { begin(any(), any()) } throws IllegalStateException("private detail")
        val slots = ServingRelaySlots(ServingRelayProperties(maxConnections = 1, maxConnectionsPerKey = 1))
        val relay = ServingRelay(transport, slots, ServingRelayProperties(), mockk<ApplicationEventPublisher>(), begin, mockk<CompleteServingUsageService>())
        // when then
        try {
            val error = shouldThrow<ServingAccessException> { relay.start(MockHttpServletRequest(), MockHttpServletResponse(), ServingRelayTarget(identity, CounterTarget("https://counter.example/v1", "test"), listOf("m")), "{}".toByteArray(), "m", false) }
            error.errorCode shouldBe ServingErrorCode.SERVING_USAGE_UNAVAILABLE
            verify(exactly = 0) { exchange.execute(any()) }
            verify(exactly = 1) { exchange.cancel() }
            slots.acquire(identity.keyId).close()
        } finally { relay.destroy() }
    }
    @Test
    fun `응답 객체가 먼저 재활용돼도 취소 사용량 이벤트를 남긴다`() {
        // given
        val identity = AuthenticatedServingKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val transport = mockk<CounterTransport>()
        val exchange = mockk<CounterExchange>(relaxed = true)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val observed = java.util.concurrent.LinkedBlockingQueue<ServingUsageObservation>()
        val body = object : java.io.InputStream() {
            override fun read(): Int {
                entered.countDown()
                while (release.count > 0) { try { release.await() } catch (_: InterruptedException) { } }
                throw java.io.IOException("client gone")
            }
        }
        every { transport.create(any(), any(), any()) } returns exchange
        every { exchange.execute(any()) } answers { firstArg<(CounterResponse) -> Unit>()(CounterResponse(200, "text/event-stream", body)) }
        val begin = mockk<BeginServingUsageService>()
        val requestId = UUID.randomUUID()
        every { begin(any(), any()) } returns requestId
        val events = ApplicationEventPublisher { event -> if (event is ServingUsageObservation) observed.add(event) }
        val relay = ServingRelay(transport, ServingRelaySlots(ServingRelayProperties()), ServingRelayProperties(), events, begin, mockk<CompleteServingUsageService>())
        val request = MockHttpServletRequest().apply { isAsyncSupported = true }
        val response = object : MockHttpServletResponse() {
            @Volatile var recycled = false
            override fun isCommitted(): Boolean {
                if (recycled) throw IllegalStateException("recycled response")
                return super.isCommitted()
            }
        }
        // when
        try {
            relay.start(request, response, ServingRelayTarget(identity, CounterTarget("https://counter.example/v1", "test"), listOf("m")), "{}".toByteArray(), "m", true)
            entered.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
            val context = request.asyncContext as org.springframework.mock.web.MockAsyncContext
            context.listeners.toList().forEach { it.onError(jakarta.servlet.AsyncEvent(context)) }
            response.recycled = true
            release.countDown()
            val result = observed.poll(5, java.util.concurrent.TimeUnit.SECONDS)
            // then
            result?.requestId shouldBe requestId
            result?.outcome shouldBe "cancelled"
        } finally { release.countDown(); relay.destroy() }
    }

}
