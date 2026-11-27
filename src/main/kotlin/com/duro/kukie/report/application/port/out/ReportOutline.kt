package com.duro.kukie.report.application.port.out

import java.util.UUID

/** AI 가 고른 턴 묶음과 요약. 변경의 사실(누가·결과)은 여기서 읽지 않는다. */
data class ReportOutline(
    val summary: String?,
    val tasks: List<Task>,
) {
    data class Task(
        val title: String,
        val summary: String?,
        val turns: List<TurnRef>,
        val followUps: List<String>,
    )

    data class TurnRef(val conversationId: UUID, val seq: Int)
}
