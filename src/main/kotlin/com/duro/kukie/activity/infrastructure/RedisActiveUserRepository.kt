package com.duro.kukie.activity.infrastructure

import com.duro.kukie.activity.domain.ActiveUserRepository
import com.duro.kukie.activity.domain.ActiveWindow
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** 분·시간 단위 HyperLogLog 에 사용자 id 를 넣고, 창에 걸친 키들을 합쳐 센다. */
@Repository
class RedisActiveUserRepository(
    private val redisTemplate: StringRedisTemplate,
) : ActiveUserRepository {

    override fun record(userId: UUID, at: Instant) {
        val hll = redisTemplate.opsForHyperLogLog()
        val minuteKey = minuteKey(at.epochSecond / 60)
        val hourKey = hourKey(at.epochSecond / 3600)
        hll.add(minuteKey, userId.toString())
        hll.add(hourKey, userId.toString())
        redisTemplate.expire(minuteKey, MINUTE_TTL)
        redisTemplate.expire(hourKey, HOUR_TTL)
    }

    override fun count(window: ActiveWindow, at: Instant): Long {
        val keys = when (window) {
            ActiveWindow.FIVE_MINUTES -> lastMinutes(at, 5)
            ActiveWindow.ONE_HOUR -> lastMinutes(at, 60)
            ActiveWindow.ONE_DAY -> lastHours(at, 24)
        }
        return redisTemplate.opsForHyperLogLog().size(*keys.toTypedArray())
    }

    private fun lastMinutes(at: Instant, count: Int): List<String> {
        val current = at.epochSecond / 60
        return (0 until count).map { minuteKey(current - it) }
    }

    private fun lastHours(at: Instant, count: Int): List<String> {
        val current = at.epochSecond / 3600
        return (0 until count).map { hourKey(current - it) }
    }

    private fun minuteKey(epochMinute: Long) = "$KEY_PREFIX:m:$epochMinute"

    private fun hourKey(epochHour: Long) = "$KEY_PREFIX:h:$epochHour"

    companion object {
        private const val KEY_PREFIX = "active-users"
        private val MINUTE_TTL = Duration.ofHours(2)
        private val HOUR_TTL = Duration.ofHours(26)
    }
}
