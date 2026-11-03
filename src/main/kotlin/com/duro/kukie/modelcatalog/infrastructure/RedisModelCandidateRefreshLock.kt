package com.duro.kukie.modelcatalog.infrastructure

import com.duro.kukie.modelcatalog.domain.ModelCandidateRefreshLock
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisModelCandidateRefreshLock(
    private val redisTemplate: StringRedisTemplate,
) : ModelCandidateRefreshLock {

    override fun tryAcquire(ttl: Duration): Boolean =
        redisTemplate.opsForValue().setIfAbsent(KEY, "1", ttl) == true

    override fun release() {
        redisTemplate.delete(KEY)
    }

    companion object {
        private const val KEY = "model-candidate:refresh-lock"
    }
}
