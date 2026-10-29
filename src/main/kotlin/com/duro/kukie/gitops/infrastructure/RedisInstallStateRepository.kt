package com.duro.kukie.gitops.infrastructure

import com.duro.kukie.gitops.domain.InstallStateRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class RedisInstallStateRepository(
    private val redisTemplate: StringRedisTemplate,
) : InstallStateRepository {

    override fun save(nonce: String, teamId: UUID, ttl: Duration) {
        redisTemplate.opsForValue().set(key(nonce), teamId.toString(), ttl)
    }

    override fun take(nonce: String): UUID? =
        redisTemplate.opsForValue().getAndDelete(key(nonce))?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun key(nonce: String) = "$KEY_PREFIX$nonce"

    companion object {
        private const val KEY_PREFIX = "github-install-state:"
    }
}
