package com.duro.kukie.report.application

import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.util.logger
import com.duro.kukie.report.domain.Report
import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.team.domain.TeamRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

@Service
class CreateScheduledReportsService(
    private val teamRepository: TeamRepository,
    private val clusterRepository: ClusterRepository,
    private val chatRunRepository: ChatRunRepository,
    private val reportRepository: ReportRepository,
    private val events: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) {

    private val log = logger()

    /** 팀마다 따로 커밋해 한 팀의 실패가 다른 팀을 막지 않게 한다. */
    operator fun invoke(now: LocalDateTime) {
        teamRepository.findReportDueIds(now).forEach { teamId ->
            try {
                transactionTemplate.executeWithoutResult { createFor(teamId, now) }
            } catch (exception: Exception) {
                log.error("주간 보고서 회차를 넘기지 못했습니다 — 다음 tick 에 다시 합니다: team={}", teamId, exception)
            }
        }
    }

    private fun createFor(teamId: UUID, now: LocalDateTime) {
        val team = teamRepository.lockReportDue(teamId, now) ?: return
        val periodEnd = team.takeDueReportSlot(now) ?: return
        val periodStart = Report.weekBefore(periodEnd, team.reportZoneId)
        val active = chatRunRepository.findSharedClusterIdsActiveBetween(team.id, periodStart, periodEnd)

        clusterRepository.findAllById(active)
            .filterNot { reportRepository.existsScheduled(it.id, periodEnd) }
            .forEach { cluster ->
                val report = reportRepository.save(Report.weekly(cluster, ownerId = null, periodStart, periodEnd, team.reportZoneId))
                events.publishEvent(ReportRequested(report.id))
            }
    }
}
