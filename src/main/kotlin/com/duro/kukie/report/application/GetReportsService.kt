package com.duro.kukie.report.application

import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.presentation.dto.response.ReportSummaryResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetReportsService(
    private val reportRepository: ReportRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID, clusterId: UUID?): List<ReportSummaryResponse> =
        reportRepository.findAllVisibleTo(userId, clusterId).map(ReportSummaryResponse::of)
}
