package com.duro.kukie.report.application

import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.domain.lockByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeleteReportService(
    private val reportPermission: ReportPermission,
    private val reportRepository: ReportRepository,
) {

    @Transactional
    operator fun invoke(reportId: UUID, userId: UUID) {
        val report = reportRepository.lockByIdOrThrow(reportId)
        reportPermission.requireReadable(report, userId)
        reportPermission.requireDeletable(report, userId)

        reportRepository.delete(report)
    }
}
