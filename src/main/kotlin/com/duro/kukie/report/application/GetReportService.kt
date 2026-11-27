package com.duro.kukie.report.application

import com.duro.kukie.report.presentation.dto.response.ReportResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetReportService(
    private val reportPermission: ReportPermission,
) {

    @Transactional(readOnly = true)
    operator fun invoke(reportId: UUID, userId: UUID): ReportResponse =
        ReportResponse.of(reportPermission.requireReadable(reportId, userId))
}
