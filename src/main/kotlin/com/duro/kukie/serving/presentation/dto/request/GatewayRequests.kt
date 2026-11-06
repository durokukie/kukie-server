package com.duro.kukie.serving.presentation.dto.request

import jakarta.validation.constraints.*
import java.util.UUID
import com.duro.kukie.serving.application.port.`in`.CompleteGatewayUsageCommand

class GatewayAuthorizeRequest(@field:NotBlank @field:Size(max = 128) val token: String) {
    override fun toString() = "GatewayAuthorizeRequest(REDACTED)"
}

data class GatewayBeginRequest(val keyId: UUID, @field:Size(min = 1, max = 200) val model: String?)

data class GatewayFinishRequest(
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

    fun toCommand(requestId: UUID) = CompleteGatewayUsageCommand(requestId, outcome, status, tokensKnown, inputTokens, outputTokens)
}
