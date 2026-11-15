package com.duro.kukie.serving.presentation.dto.response

import java.time.LocalDateTime

data class ServingUsageOverviewTotals(
    val requests: Long, val completed: Long, val failed: Long, val inProgress: Long, val rejected: Long,
    val durationP50Ms: Long?, val durationP95Ms: Long?, val ttfbP50Ms: Long?, val ttfbP95Ms: Long?,
    val inputTokens: Long, val outputTokens: Long,
)
data class ServingRejectionCount(val reason: String, val count: Long)
data class ServingUsageHour(val hour: LocalDateTime, val requests: Long, val failed: Long, val rejected: Long, val ttfbP95Ms: Long?)
data class ServingUsageRecent(
    val startedAt: LocalDateTime, val model: String?, val outcome: String, val status: Int?, val durationMs: Long?, val ttfbMs: Long?,
    val inputTokens: Long?, val outputTokens: Long?, val userEmail: String,
)
data class ServingUsageOverviewResponse(
    val asOf: LocalDateTime, val from: LocalDateTime, val model: String?, val models: List<String>,
    val totals: ServingUsageOverviewTotals, val rejectedByReason: List<ServingRejectionCount>,
    val hourly: List<ServingUsageHour>, val recent: List<ServingUsageRecent>,
)
