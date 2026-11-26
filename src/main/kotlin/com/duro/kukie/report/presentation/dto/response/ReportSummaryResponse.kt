package com.duro.kukie.report.presentation.dto.response

import com.duro.kukie.report.domain.ReportKind
import com.duro.kukie.report.domain.ReportStatus
import com.duro.kukie.report.domain.ReportSummary
import java.time.LocalDateTime
import java.util.UUID

data class ReportSummaryResponse(
    val id: UUID,
    val kind: ReportKind,
    val clusterId: UUID,
    val conversationId: UUID?,
    val ownerId: UUID?,
    val shared: Boolean,
    val title: String,
    val status: ReportStatus,
    val periodStart: LocalDateTime?,
    val periodEnd: LocalDateTime?,
    val aiRefined: Boolean,
    val version: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun of(summary: ReportSummary) = ReportSummaryResponse(
            id = summary.id,
            kind = summary.kind,
            clusterId = summary.clusterId,
            conversationId = summary.conversationId,
            ownerId = summary.ownerId,
            shared = summary.shared,
            title = summary.title,
            status = summary.status,
            periodStart = summary.periodStart,
            periodEnd = summary.periodEnd,
            aiRefined = summary.aiRefined,
            version = summary.version,
            createdAt = summary.createdAt,
            updatedAt = summary.updatedAt,
        )
    }
}
