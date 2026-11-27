package com.duro.kukie.report.application

import com.duro.kukie.report.domain.ReportRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
class ResumeStalledReportsService(
    private val reportRepository: ReportRepository,
    private val events: ApplicationEventPublisher,
) {

    @Transactional
    operator fun invoke() {
        // updated_at 은 JPA 감사가 시스템 시간대로 적는다
        val now = LocalDateTime.now()
        val ids = reportRepository.lockStalledIds(now.minus(STALLED_AFTER), now.minus(GIVE_UP_AFTER), BATCH)
        if (ids.isEmpty()) return

        // 끝나기 전에 다음 tick 이 또 넘기지 않게 한다
        reportRepository.touch(ids, now)
        ids.forEach { events.publishEvent(ReportRequested(it)) }
    }

    companion object {
        val STALLED_AFTER: Duration = Duration.ofMinutes(10)
        val GIVE_UP_AFTER: Duration = Duration.ofDays(1)
        private const val BATCH = 20
    }
}
