package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.auth.domain.RefreshTokenRotation
import com.duro.kukie.global.config.properties.JwtProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * 세션 하나 = 해시 `refresh-token:{userId}:<sessionId>` (cur·prev·rotatedAt), TTL 은 리프레시 토큰 수명이고 쓸 때마다 늘린다.
 * 사용자 세션 목록 = ZSET `refresh-token:{userId}:sessions` (점수 = 마지막으로 쓴 시각). 상한을 넘기면 점수가 낮은 것부터 지운다.
 * `{userId}` 해시 태그로 한 사용자의 키를 같은 슬롯에 둬서 스크립트가 원자적으로 다룬다.
 * 세션 도입 전 키 `refresh-token:<userId>` 는 옛 토큰을 옮기는 동안만 읽는다.
 */
@Repository
class RedisRefreshTokenRepository(
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties,
    private val clock: Clock = Clock.systemUTC(),
) : RefreshTokenRepository {

    override fun create(userId: UUID, sessionId: String, refreshToken: String) {
        redisTemplate.execute(
            CREATE,
            listOf(sessionKey(userId, sessionId), indexKey(userId)),
            sessionId,
            refreshToken,
            now().toString(),
            ttlMillis().toString(),
            MAX_SESSIONS_PER_USER.toString(),
            sessionKeyPrefix(userId),
            (now() - ttlMillis()).toString(),
        )
    }

    override fun rotate(userId: UUID, sessionId: String, presented: String, next: String): RefreshTokenRotation {
        val result = redisTemplate.execute(
            ROTATE,
            listOf(sessionKey(userId, sessionId), indexKey(userId)),
            sessionId,
            presented,
            next,
            now().toString(),
            ttlMillis().toString(),
            REUSE_GRACE.toMillis().toString(),
        )
        return when (result.firstOrNull()) {
            "rotated" -> RefreshTokenRotation.Rotated
            "recent" -> RefreshTokenRotation.RecentlyRotated(result[1].toString())
            "reused" -> RefreshTokenRotation.Reused
            else -> RefreshTokenRotation.Missing
        }
    }

    override fun delete(userId: UUID, sessionId: String) {
        redisTemplate.execute(DELETE, listOf(sessionKey(userId, sessionId), indexKey(userId)), sessionId)
    }

    override fun deleteAll(userId: UUID) {
        redisTemplate.execute(DELETE_ALL, listOf(indexKey(userId)), sessionKeyPrefix(userId))
        deleteLegacy(userId)
    }

    override fun takeLegacy(userId: UUID, presented: String): Boolean =
        redisTemplate.execute(TAKE_LEGACY, listOf(legacyKey(userId)), presented, REUSE_GRACE.toMillis().toString()) == 1L

    override fun deleteLegacy(userId: UUID) {
        redisTemplate.delete(legacyKey(userId))
    }

    private fun now(): Long = clock.millis()

    private fun ttlMillis(): Long = jwtProperties.refreshTokenExpiration.toMillis()

    private fun sessionKeyPrefix(userId: UUID) = "$KEY_PREFIX{$userId}:"

    private fun sessionKey(userId: UUID, sessionId: String) = sessionKeyPrefix(userId) + sessionId

    private fun indexKey(userId: UUID) = sessionKeyPrefix(userId) + INDEX_SUFFIX

    private fun legacyKey(userId: UUID) = "$KEY_PREFIX$userId"

    companion object {
        private const val KEY_PREFIX = "refresh-token:"
        private const val INDEX_SUFFIX = "sessions"

        /** 한 사용자가 동시에 유지하는 로그인 수. 넘으면 가장 오래 안 쓴 것부터 끊는다. */
        const val MAX_SESSIONS_PER_USER = 10

        /** 같은 브라우저의 두 탭이 같은 쿠키로 동시에 재발급할 때 늦은 쪽을 재사용으로 보지 않는 시간. */
        val REUSE_GRACE: Duration = Duration.ofSeconds(10)

        // KEYS: 세션, 목록 / ARGV: sessionId, token, now, ttlMillis, max, sessionKeyPrefix, 만료로 볼 점수(이것보다 작으면 이미 만료된 세션)
        private val CREATE = DefaultRedisScript(
            """
            redis.call('HSET', KEYS[1], 'cur', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', '(' .. ARGV[7])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
            local extra = redis.call('ZCARD', KEYS[2]) - tonumber(ARGV[5])
            if extra > 0 then
              local evicted = redis.call('ZRANGE', KEYS[2], 0, extra - 1)
              for _, sid in ipairs(evicted) do
                redis.call('DEL', ARGV[6] .. sid)
                redis.call('ZREM', KEYS[2], sid)
              end
            end
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        // KEYS: 세션, 목록 / ARGV: sessionId, presented, next, now, ttlMillis, graceMillis
        @Suppress("UNCHECKED_CAST")
        private val ROTATE = DefaultRedisScript(
            """
            local cur = redis.call('HGET', KEYS[1], 'cur')
            if not cur then
              redis.call('ZREM', KEYS[2], ARGV[1])
              return {'missing'}
            end
            if cur == ARGV[2] then
              redis.call('HSET', KEYS[1], 'cur', ARGV[3], 'prev', ARGV[2], 'rotatedAt', ARGV[4])
              redis.call('PEXPIRE', KEYS[1], ARGV[5])
              redis.call('ZADD', KEYS[2], ARGV[4], ARGV[1])
              redis.call('PEXPIRE', KEYS[2], ARGV[5])
              return {'rotated'}
            end
            local prev = redis.call('HGET', KEYS[1], 'prev')
            local rotatedAt = tonumber(redis.call('HGET', KEYS[1], 'rotatedAt') or '0')
            if prev == ARGV[2] and tonumber(ARGV[4]) - rotatedAt <= tonumber(ARGV[6]) then
              return {'recent', cur}
            end
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            return {'reused'}
            """.trimIndent(),
            List::class.java as Class<List<Any>>,
        )

        // KEYS: 세션, 목록 / ARGV: sessionId
        private val DELETE = DefaultRedisScript(
            """
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        // KEYS: 목록 / ARGV: sessionKeyPrefix
        private val DELETE_ALL = DefaultRedisScript(
            """
            for _, sessionId in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
              redis.call('DEL', ARGV[1] .. sessionId)
            end
            redis.call('DEL', KEYS[1])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        // 맞으면 바로 지우지 않고 겹침 시간 뒤에 사라지게 한다. 배포 직후 같은 브라우저 두 탭이 같은 옛 토큰으로 동시에 와도 둘 다 옮겨 간다
        // KEYS: 옛 키 / ARGV: presented, graceMillis
        private val TAKE_LEGACY = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 or ttl > tonumber(ARGV[2]) then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}
