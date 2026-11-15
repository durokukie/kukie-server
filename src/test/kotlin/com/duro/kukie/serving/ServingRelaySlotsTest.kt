package com.duro.kukie.serving

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.application.ServingRelaySlots
import com.duro.kukie.serving.exception.ServingAccessException
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.util.UUID

class ServingRelaySlotsTest {
    @Test
    fun `키별 상한과 서버 전체 상한을 각각 적용하고 중복 반환은 무시한다`() {
        // given
        val slots = ServingRelaySlots(ServingRelayProperties(maxConnections = 2, maxConnectionsPerKey = 1))
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        // when then
        val first = slots.acquire(a)
        shouldThrow<ServingAccessException> { slots.acquire(a) }
        val second = slots.acquire(b)
        shouldThrow<ServingAccessException> { slots.acquire(c) }
        first.close()
        first.close()
        slots.acquire(c).use { shouldThrow<ServingAccessException> { slots.acquire(a) } }
        second.close()
        slots.acquire(a).close()
    }
}
