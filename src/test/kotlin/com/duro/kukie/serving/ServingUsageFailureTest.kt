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
}
