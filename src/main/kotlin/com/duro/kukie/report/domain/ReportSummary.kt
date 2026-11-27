package com.duro.kukie.report.domain

import java.time.LocalDateTime
import java.util.UUID

/** 목록용. 본문(content)을 읽지 않는다. */
data class ReportSummary(
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
)
