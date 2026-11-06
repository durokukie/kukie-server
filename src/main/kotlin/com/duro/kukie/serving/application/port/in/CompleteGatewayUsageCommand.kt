package com.duro.kukie.serving.application.port.`in`

import java.util.UUID

data class CompleteGatewayUsageCommand(val requestId: UUID, val outcome: String, val status: Int, val tokensKnown: Boolean, val inputTokens: Long?, val outputTokens: Long?)
