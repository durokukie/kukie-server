package com.duro.kukie.serving.application

import java.time.Instant

// 본문 없는 집계 이벤트 계약이며 저장·기간별 조회는 후속 소비자가 담당한다.
data class ServingUsageObservation(
    val identity: AuthenticatedServingKey,
    val model: String?,
    val startedAt: Instant,
    val durationMillis: Long,
    val httpStatus: Int,
    val outcome: String,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val usageMissing: Boolean,
)
