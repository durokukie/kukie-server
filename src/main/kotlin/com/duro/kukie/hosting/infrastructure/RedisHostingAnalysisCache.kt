package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.hosting.application.port.out.HostingAnalysisCache
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisHostingAnalysisCache(
    private val redisTemplate: StringRedisTemplate,
) : HostingAnalysisCache {

    override fun get(key: String): String? = redisTemplate.opsForValue().get(prefixed(key))

    override fun put(key: String, value: String) {
        redisTemplate.opsForValue().set(prefixed(key), value, TTL)
    }

    private fun prefixed(key: String) = "hosting:analysis:$key"

    companion object {
        // 배포 기록이 30일 뒤 지워진다
        private val TTL: Duration = Duration.ofDays(30)
    }
}
