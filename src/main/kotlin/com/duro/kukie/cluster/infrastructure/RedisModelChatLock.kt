package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.domain.ModelChatLock
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class RedisModelChatLock(
    private val redisTemplate: StringRedisTemplate,
) : ModelChatLock {

    override fun tryAcquire(userId: UUID, ttl: Duration): String? {
        val token = UUID.randomUUID().toString()
        return token.takeIf { redisTemplate.opsForValue().setIfAbsent(key(userId), token, ttl) == true }
    }

    override fun holdFor(userId: UUID, token: String, ttl: Duration) {
        redisTemplate.execute(EXPIRE_IF_OWNER, listOf(key(userId)), token, ttl.toMillis().toString())
    }

    override fun release(userId: UUID, token: String) {
        redisTemplate.execute(RELEASE_IF_OWNER, listOf(key(userId)), token)
    }

    private fun key(userId: UUID) = "model-chat:lock:$userId"

    companion object {
        private val EXPIRE_IF_OWNER = DefaultRedisScript(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long::class.javaObjectType,
        )
        private val RELEASE_IF_OWNER = DefaultRedisScript(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long::class.javaObjectType,
        )
    }
}
