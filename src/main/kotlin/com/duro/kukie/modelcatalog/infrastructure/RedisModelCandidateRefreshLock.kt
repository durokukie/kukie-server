package com.duro.kukie.modelcatalog.infrastructure

import com.duro.kukie.modelcatalog.domain.ModelCandidateRefreshLock
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class RedisModelCandidateRefreshLock(
    private val redisTemplate: StringRedisTemplate,
) : ModelCandidateRefreshLock {

    override fun tryAcquire(ttl: Duration): String? {
        val token = UUID.randomUUID().toString()
        return token.takeIf { redisTemplate.opsForValue().setIfAbsent(KEY, token, ttl) == true }
    }

    override fun release(token: String) {
        redisTemplate.execute(RELEASE_IF_OWNER, listOf(KEY), token)
    }

    companion object {
        private const val KEY = "model-candidate:refresh-lock"
        private val RELEASE_IF_OWNER = DefaultRedisScript(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long::class.javaObjectType,
        )
    }
}
