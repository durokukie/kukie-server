package com.duro.kukie.serving.application

import java.time.Instant


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
    val requestId: java.util.UUID = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch(),
    val wireStatus: Int? = null,
    val ttfbMillis: Long? = null,
)

data class ServingRejectionObservation(val id: java.util.UUID, val identity: AuthenticatedServingKey, val model: String?, val reason: String, val minute: Instant, val count: Int)
