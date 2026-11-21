package com.duro.kukie.activity.infrastructure

import com.duro.kukie.activity.domain.ActiveUserRepository
import com.duro.kukie.activity.domain.ActiveWindow
import com.duro.kukie.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RedisActiveUserRepositoryIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var activeUserRepository: ActiveUserRepository

    private val now = Instant.parse("2026-11-21T01:30:00Z")

    @Test
    fun `창 안에 기록된 서로 다른 사용자 수를 센다`() {
        // given
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        activeUserRepository.record(alice, now)
        activeUserRepository.record(alice, now.minus(Duration.ofMinutes(2)))
        activeUserRepository.record(bob, now.minus(Duration.ofMinutes(20)))
        activeUserRepository.record(UUID.randomUUID(), now.minus(Duration.ofHours(5)))

        // when & then
        activeUserRepository.count(ActiveWindow.FIVE_MINUTES, now) shouldBe 1
        activeUserRepository.count(ActiveWindow.ONE_HOUR, now) shouldBe 2
        activeUserRepository.count(ActiveWindow.ONE_DAY, now) shouldBe 3
    }

    @Test
    fun `창보다 오래된 기록은 세지 않는다`() {
        // given
        activeUserRepository.record(UUID.randomUUID(), now.minus(Duration.ofHours(25)))

        // when & then
        activeUserRepository.count(ActiveWindow.ONE_DAY, now) shouldBe 0
    }
}
