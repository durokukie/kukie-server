package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.global.config.properties.JwtProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class RedisRefreshTokenRepository(
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties,
) : RefreshTokenRepository {

    override fun save(userId: UUID, refreshToken: String) {
        redisTemplate.opsForValue()
            .set(key(userId), refreshToken, jwtProperties.refreshTokenExpiration)
    }

    override fun findByUserId(userId: UUID): String? {
        return redisTemplate.opsForValue().get(key(userId))
    }

    override fun deleteByUserId(userId: UUID) {
        redisTemplate.delete(key(userId))
    }

    private fun key(userId: UUID) = "$KEY_PREFIX$userId"

    companion object {
        private const val KEY_PREFIX = "refresh-token:"
    }
}
