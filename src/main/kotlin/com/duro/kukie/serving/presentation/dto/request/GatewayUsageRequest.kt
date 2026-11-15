package com.duro.kukie.serving.presentation.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.UUID

data class GatewayUsageRequest(
    @field:NotNull @field:Size(max = 500) @field:Valid val records: List<GatewayUsageRecord?>?,
    @field:Size(max = 500) @field:Valid val rejections: List<GatewayRejectionRecord?>? = emptyList(),
) {
    @get:AssertTrue(message = "사용량 기록에 빈 항목이 있습니다.")
    val validRecords: Boolean get() = records != null && records.none { it == null } && rejections?.none { it == null } != false &&
        (records.isNotEmpty() || !rejections.isNullOrEmpty())
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
    @field:Min(0) val ttfbMillis: Long? = null,
) {
    @get:AssertTrue(message = "완료 상태와 HTTP 상태 또는 토큰 정보가 일치하지 않습니다.")
    val consistent: Boolean get() = (status == 0 || status in 100..599) &&
        (outcome != "COMPLETED" || status == 200) &&
        (!tokensKnown || (inputTokens != null && outputTokens != null))
}

/** 게이트웨이가 중계 전에 거절한 요청을 키·모델·사유·분 단위로 센 것. id로 재전송을 구분한다. */
data class GatewayRejectionRecord(
    val id: UUID,
    val keyId: UUID,
    @field:Size(min = 1, max = 200) val model: String?,
    @field:Pattern(regexp = "concurrency_limit|connector_busy|model_not_allowed|invalid_request|request_too_large|upstream_unavailable") val reason: String,
    val minute: Instant,
    @field:Min(1) val count: Int,
)
