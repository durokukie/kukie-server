package com.duro.kukie.user.infrastructure

import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.domain.VerificationType
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class RedisVerificationCodeRepository(
    private val redisTemplate: StringRedisTemplate,
) : VerificationCodeRepository {

    override fun save(type: VerificationType, email: Email, code: String) {
        redisTemplate.opsForValue()
            .set(key(type, email), code, VerificationCodeRepository.EXPIRATION)
    }

    override fun findByEmail(type: VerificationType, email: Email): String? {
        return redisTemplate.opsForValue().get(key(type, email))
    }

    override fun deleteByEmail(type: VerificationType, email: Email) {
        redisTemplate.delete(key(type, email))
    }

    private fun key(purpose: VerificationType, email: Email) = "${prefix(purpose)}${email.value}"

    private fun prefix(purpose: VerificationType) = when (purpose) {
        VerificationType.SIGN_UP -> "verification-code:"
        VerificationType.PASSWORD_RESET -> "password-reset-code:"
    }
}
