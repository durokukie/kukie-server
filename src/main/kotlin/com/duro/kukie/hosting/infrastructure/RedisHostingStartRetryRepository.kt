package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.hosting.domain.HostingStartRetry
import com.duro.kukie.hosting.domain.HostingStartRetryRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Repository
class RedisHostingStartRetryRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : HostingStartRetryRepository {

    override fun claim(key: String, retry: HostingStartRetry): Boolean =
        redisTemplate.opsForValue().setIfAbsent(prefixed(key), objectMapper.writeValueAsString(retry), TTL) == true

    override fun find(key: String): HostingStartRetry? =
        redisTemplate.opsForValue().get(prefixed(key))?.let { runCatching { objectMapper.readValue(it, HostingStartRetry::class.java) }.getOrNull() }

    override fun save(key: String, retry: HostingStartRetry) {
        redisTemplate.opsForValue().set(prefixed(key), objectMapper.writeValueAsString(retry), TTL)
    }

    private fun prefixed(key: String) = "hosting:start-retry:$key"

    companion object {
        // 배포 기록이 30일 뒤 지워진다
        private val TTL: Duration = Duration.ofDays(30)
    }
}
