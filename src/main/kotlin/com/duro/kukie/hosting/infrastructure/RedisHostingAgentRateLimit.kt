package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisHostingAgentRateLimit(
    private val redisTemplate: StringRedisTemplate,
) : HostingAgentRateLimit {

    // ponytail: 고정 창이라 경계에서 두 배까지 들어올 수 있다. 정확해야 하면 미끄러지는 창으로 바꾼다
    override fun tryAcquire(key: String, limit: Int, window: Duration): Boolean =
        (redisTemplate.execute(COUNT, listOf("hosting:agent:rate:$key"), window.seconds.toString()) ?: Long.MAX_VALUE) <= limit

    companion object {
        // 첫 요청이 창을 연다. 셈과 만료를 한 번에 해서, 만료가 빠진 키가 영구히 막는 일이 없다
        private val COUNT = RedisScript.of<Long>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
            return count
            """.trimIndent(),
            Long::class.java,
        )
    }
}
