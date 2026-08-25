package com.duro.kukie.user.infrastructure

import com.duro.kukie.user.domain.VerificationCodeRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisVerificationCodeRepository(
    private val redisTemplate: StringRedisTemplate,
) : VerificationCodeRepository {

    override fun save(email: String, code: String) {
        redisTemplate.opsForValue()
            .set(key(email), code, Duration.ofMinutes(3))
    }

    override fun findByEmail(email: String): String? {
        return redisTemplate.opsForValue().get(key(email))
    }

    override fun deleteByEmail(email: String) {
        redisTemplate.delete(key(email))
    }

    private fun key(email: String) = "$KEY_PREFIX$email"

    companion object {
        private const val KEY_PREFIX = "verification-code:"
    }
}
