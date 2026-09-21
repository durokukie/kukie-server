package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.domain.HandoffCodeRepository
import com.duro.kukie.auth.domain.HandoffTicket
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class RedisHandoffCodeRepository(
    private val redisTemplate: StringRedisTemplate,
) : HandoffCodeRepository {

    override fun save(code: String, ticket: HandoffTicket, ttl: Duration) {
        redisTemplate.opsForValue().set(key(code), "${ticket.userId} ${ticket.codeChallenge}", ttl)
    }

    /** GETDEL 한 번으로 읽고 지운다 — 읽기와 지우기 사이에 다른 요청이 끼어 같은 코드를 두 번 쓰는 일이 없다. */
    override fun take(code: String): HandoffTicket? {
        val raw = redisTemplate.opsForValue().getAndDelete(key(code)) ?: return null
        val (userId, challenge) = raw.split(' ', limit = 2).takeIf { it.size == 2 } ?: return null
        return HandoffTicket(UUID.fromString(userId), challenge)
    }

    private fun key(code: String) = "$KEY_PREFIX$code"

    companion object {
        private const val KEY_PREFIX = "handoff-code:"
    }
}
