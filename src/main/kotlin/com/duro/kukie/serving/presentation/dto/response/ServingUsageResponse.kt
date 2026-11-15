package com.duro.kukie.serving.presentation.dto.response

import java.time.LocalDate
import java.time.LocalDateTime

data class ServingUsageTotals(
    val requests: Long, val completed: Long, val failed: Long, val inProgress: Long,
    val inputTokens: Long, val outputTokens: Long, val missingUsage: Long,
)
data class ServingUsageGroup(val id: String, val totals: ServingUsageTotals)
data class ServingUsageResponse(
    val from: LocalDate, val to: LocalDate, val asOf: LocalDateTime, val lastRecordedAt: LocalDateTime?,
    val retentionDays: Int, val totals: ServingUsageTotals,
    val daily: List<ServingUsageGroup>, val byKey: List<ServingUsageGroup>, val byModel: List<ServingUsageGroup>,
    val byUser: List<ServingUsageGroup>,
)
