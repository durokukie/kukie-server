package com.duro.kukie.report.application.port.out

import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import com.duro.kukie.report.domain.ReportKind
import java.time.Instant

/** 주간 보고서는 대화마다 기간 안의 턴만 담는다. */
data class ReportSource(
    val kind: ReportKind,
    val clusterName: String,
    val periodStart: Instant?,
    val periodEnd: Instant?,
    val conversations: List<WorklogResponse>,
)
