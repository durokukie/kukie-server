package com.duro.kukie.report.application

import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.report.exception.TooManyReportsException
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class ReportQuota(
    private val rateLimit: HostingAgentRateLimit,
) {

    /** 보고서마다 AI 를 부르므로 한 사람이 직접 만드는 수를 막는다. 자동 주간 보고서는 세지 않는다. */
    fun consume(userId: UUID) {
        if (!rateLimit.tryAcquire("report:$userId", HOURLY_LIMIT, Duration.ofHours(1))) throw TooManyReportsException()
    }

    companion object {
        const val HOURLY_LIMIT = 30
    }
}
