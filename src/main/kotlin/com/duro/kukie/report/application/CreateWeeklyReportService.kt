package com.duro.kukie.report.application

import com.duro.kukie.cluster.application.ClusterPermission
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.report.domain.Report
import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.presentation.dto.response.ReportResponse
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.util.UUID

@Service
class CreateWeeklyReportService(
    private val clusterPermission: ClusterPermission,
    private val reportQuota: ReportQuota,
    private val teamRepository: TeamRepository,
    private val reportRepository: ReportRepository,
    private val events: ApplicationEventPublisher,
) {

    @Transactional
    operator fun invoke(clusterId: UUID, userId: UUID, zone: ZoneId?): ReportResponse {
        val cluster = clusterPermission.requireReadable(clusterId, userId)
        reportQuota.consume(userId)
        val reportZone = zone ?: teamRepository.findByIdOrThrow(cluster.teamId).reportZoneId
        val now = utcNow()
        val weekAgo = Report.weekBefore(now, reportZone)
        // 마지막 자동 보고서가 담은 기간은 다시 담지 않는다
        val periodStart = reportRepository.findLastScheduledEnd(cluster.id)?.takeIf { it.isAfter(weekAgo) } ?: weekAgo
        val report = reportRepository.save(Report.weekly(cluster, userId, periodStart, now, reportZone))
        events.publishEvent(ReportRequested(report.id))

        return ReportResponse.of(report)
    }
}
