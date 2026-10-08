package com.duro.kukie.user.infrastructure

import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.domain.VerificationCodeRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisVerificationCodeRepository(
    private val redisTemplate: StringRedisTemplate,
) : VerificationCodeRepository {

    override fun save(email: Email, code: String) {
        redisTemplate.opsForValue()
            .set(key(email), code, VerificationCodeRepository.EXPIRATION)
    }

    override fun findByEmail(email: Email): String? {
        return redisTemplate.opsForValue().get(key(email))
    }

    override fun deleteByEmail(email: Email) {
        redisTemplate.delete(key(email))
    }

    private fun key(email: Email) = "$KEY_PREFIX${email.value}"

    companion object {
        private const val KEY_PREFIX = "verification-code:"
    }
}
