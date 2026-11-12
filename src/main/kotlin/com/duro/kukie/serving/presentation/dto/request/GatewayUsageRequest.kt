package com.duro.kukie.serving.presentation.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.UUID

data class GatewayUsageRequest(@field:NotNull @field:Size(min = 1, max = 500) @field:Valid val records: List<GatewayUsageRecord?>?) {
    @get:AssertTrue(message = "사용량 기록에 빈 항목이 있습니다.")
    val validRecords: Boolean get() = records != null && records.none { it == null }
}

data class GatewayUsageRecord(
    val requestId: UUID,
    val keyId: UUID,
    @field:Size(min = 1, max = 200) val model: String?,
    val startedAt: Instant,
    @field:Min(0) @field:Max(86_400_000) val durationMillis: Long,
    @field:Pattern(regexp = "COMPLETED|FAILED|CANCELLED|TIMEOUT|INCOMPLETE") val outcome: String,
    @field:Min(0) @field:Max(599) val status: Int,
    val tokensKnown: Boolean,
    @field:Min(0) val inputTokens: Long?,
    @field:Min(0) val outputTokens: Long?,
) {
    @get:AssertTrue(message = "완료 상태와 HTTP 상태 또는 토큰 정보가 일치하지 않습니다.")
    val consistent: Boolean get() = (status == 0 || status in 100..599) &&
        (outcome != "COMPLETED" || status == 200) &&
        (!tokensKnown || (inputTokens != null && outputTokens != null))
}
