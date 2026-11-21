package com.duro.kukie.activity.application

import com.duro.kukie.activity.domain.ActiveUserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class RecordActiveUserService(
    private val activeUserRepository: ActiveUserRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val recordedMinute = ConcurrentHashMap<UUID, Instant>()

    /** 인증된 요청마다 불린다. 같은 사용자는 1분에 한 번만 Redis 에 쓰고, 실패해도 요청은 막지 않는다. */
    operator fun invoke(userId: UUID) {
        val now = clock.instant()
        val minute = now.truncatedTo(ChronoUnit.MINUTES)
        if (recordedMinute.put(userId, minute) == minute) return
        if (recordedMinute.size > MAX_REMEMBERED) recordedMinute.entries.removeIf { it.value != minute }
        runCatching { activeUserRepository.record(userId, now) }
            .onFailure { log.warn("active user record failed: {}", it.message) }
    }

    companion object {
        private const val MAX_REMEMBERED = 10_000
    }
}
