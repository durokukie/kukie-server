package com.duro.kukie.report.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.report.application.port.`in`.UpdateReportCommand
import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.domain.lockByIdOrThrow
import com.duro.kukie.report.exception.ReportChangedException
import com.duro.kukie.report.exception.ReportNotReadyException
import com.duro.kukie.report.presentation.dto.response.ReportResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateReportService(
    private val reportPermission: ReportPermission,
    private val reportRepository: ReportRepository,
) {

    /** 읽을 수 있으면 고칠 수 있다. */
    @Transactional
    operator fun invoke(command: UpdateReportCommand): ReportResponse {
        val report = reportRepository.lockByIdOrThrow(command.reportId)
        reportPermission.requireReadable(report, command.userId)
        if (report.isGenerating) throw ReportNotReadyException()
        if (report.version != command.version) throw ReportChangedException()

        report.edit(command.title, command.content, command.userId, utcNow())

        return ReportResponse.of(report)
    }
}
