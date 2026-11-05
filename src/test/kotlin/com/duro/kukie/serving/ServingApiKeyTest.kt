package com.duro.kukie.serving

import com.duro.kukie.serving.domain.ServingApiKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ServingApiKeyTest {
    @Test
    fun `만료 시각 직전만 사용 가능하고 같은 시각부터는 거절한다`() {
        // given
        val expires = LocalDateTime.of(2026, 11, 6, 0, 0)
        val key = ServingApiKey(UUID.randomUUID(), "개발", "0".repeat(64), "kk_123456789", expires)
        // when then
        key.isUsable(expires.minusNanos(1)) shouldBe true
        key.isUsable(expires) shouldBe false
        key.isUsable(expires.plusNanos(1)) shouldBe false
    }
}
