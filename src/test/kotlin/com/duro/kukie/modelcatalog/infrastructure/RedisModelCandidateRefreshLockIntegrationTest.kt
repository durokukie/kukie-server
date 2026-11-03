package com.duro.kukie.modelcatalog.infrastructure

import com.duro.kukie.modelcatalog.domain.ModelCandidateRefreshLock
import com.duro.kukie.support.IntegrationTest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration

class RedisModelCandidateRefreshLockIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var lock: ModelCandidateRefreshLock

    @Test
    fun `잠금을 잡은 쪽의 토큰으로만 풀 수 있다`() {
        // given
        val token = lock.tryAcquire(TTL).shouldNotBeNull()
        lock.tryAcquire(TTL).shouldBeNull()

        // when
        lock.release("다른-토큰")

        // then
        lock.tryAcquire(TTL).shouldBeNull()

        // when
        lock.release(token)

        // then
        lock.tryAcquire(TTL).shouldNotBeNull()
    }

    @Test
    fun `만료 뒤 늦게 실패한 요청은 새로 잡은 잠금을 풀지 못한다`() {
        // given
        val expired = lock.tryAcquire(Duration.ofMillis(50)).shouldNotBeNull()
        Thread.sleep(150)
        lock.tryAcquire(TTL).shouldNotBeNull()

        // when
        lock.release(expired)

        // then
        lock.tryAcquire(TTL).shouldBeNull()
    }

    companion object {
        private val TTL: Duration = Duration.ofMinutes(1)
    }
}
