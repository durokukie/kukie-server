package com.duro.kukie.user.domain

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class VerificationCodeRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun save(email: String, code: String) {
        redisTemplate.opsForValue()
            .set(key(email), code, Duration.ofMinutes(3))
    }

    fun get(email: String): String? {
        return redisTemplate.opsForValue().get(key(email))
    }

    fun delete(email: String) {
        redisTemplate.delete(key(email))
    }

    private fun key(email: String) = "$KEY_PREFIX$email"

    companion object {
        private const val KEY_PREFIX = "verification-code:"
    }
}
