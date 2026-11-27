package com.duro.kukie.report.presentation.dto.response

import com.duro.kukie.report.domain.Report
import com.duro.kukie.report.domain.ReportKind
import com.duro.kukie.report.domain.ReportStatus
import java.time.LocalDateTime
import java.util.UUID

data class ReportResponse(
    val id: UUID,
    val kind: ReportKind,
    val clusterId: UUID,
    val conversationId: UUID?,
    /** 자동으로 만든 주간 보고서는 null */
    val ownerId: UUID?,
    val shared: Boolean,
    val title: String,
    val status: ReportStatus,
    /** Markdown. 만드는 중(GENERATING)이면 null */
    val content: String?,
    val periodStart: LocalDateTime?,
    val periodEnd: LocalDateTime?,
    /** 대화 보고서를 만들 때 끝나 있던 마지막 턴 번호. 진행 중이던 턴은 세지 않는다 */
    val throughTurn: Int?,
    /** false 면 AI 정리 없이 변경만 담은 기본형이다 */
    val aiRefined: Boolean,
    val timeZone: String,
    val version: Int,
    val editedBy: UUID?,
    val editedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun of(report: Report) = ReportResponse(
            id = report.id,
            kind = report.kind,
            clusterId = report.clusterId,
            conversationId = report.conversationId,
            ownerId = report.ownerId,
            shared = report.shared,
            title = report.title,
            status = report.status,
            content = report.content,
            periodStart = report.periodStart,
            periodEnd = report.periodEnd,
            throughTurn = report.throughTurn,
            aiRefined = report.aiRefined,
            timeZone = report.timeZone,
            version = report.version,
            editedBy = report.editedBy,
            editedAt = report.editedAt,
            createdAt = report.createdAt,
            updatedAt = report.updatedAt,
        )
    }
}
