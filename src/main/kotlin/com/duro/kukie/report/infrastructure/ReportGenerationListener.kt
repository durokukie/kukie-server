package com.duro.kukie.report.infrastructure

import com.duro.kukie.global.util.logger
import com.duro.kukie.report.application.GenerateReportService
import com.duro.kukie.report.application.ReportRequested
import com.duro.kukie.report.application.port.out.ReportTaskRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class ReportGenerationListener(
    private val reportTaskRunner: ReportTaskRunner,
    private val generateReportService: GenerateReportService,
) {

    private val log = logger()

    // 차례를 기다리는 보고서가 멈춘 보고서로 잡혀 또 넘어와도 두 번 만들지 않는다
    private val inFlight = ConcurrentHashMap.newKeySet<UUID>()

    /** 요청·스케줄러 스레드가 생성을 기다리지 않게 커밋 뒤에 넘긴다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ReportRequested) {
        if (!inFlight.add(event.reportId)) return

        reportTaskRunner.run {
            try {
                generateReportService(event.reportId)
            } catch (exception: Exception) {
                log.error("보고서를 만들지 못했습니다 — 멈춘 보고서 확인 때 다시 합니다: report={}", event.reportId, exception)
            } finally {
                inFlight.remove(event.reportId)
            }
        }
    }
}
