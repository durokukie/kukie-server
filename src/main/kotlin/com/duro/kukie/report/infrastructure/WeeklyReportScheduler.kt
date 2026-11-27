package com.duro.kukie.report.infrastructure

import com.duro.kukie.global.util.logger
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.report.application.CreateScheduledReportsService
import com.duro.kukie.report.application.ResumeStalledReportsService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "report", name = ["scheduler-enabled"], havingValue = "true", matchIfMissing = true)
class WeeklyReportScheduler(
    private val createScheduledReportsService: CreateScheduledReportsService,
    private val resumeStalledReportsService: ResumeStalledReportsService,
) {

    private val log = logger()

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    fun tick() {
        attempt("주간 보고서 회차 확인") { createScheduledReportsService(utcNow()) }
        attempt("멈춘 보고서 확인") { resumeStalledReportsService() }
    }

    private fun attempt(what: String, work: () -> Unit) {
        try {
            work()
        } catch (exception: Exception) {
            log.error("{}에 실패했습니다", what, exception)
        }
    }
}
