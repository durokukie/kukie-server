package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ServingUsageRepository
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import com.duro.kukie.serving.application.port.`in`.CompleteGatewayUsageCommand
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID

@Service
class CompleteGatewayUsageService(private val usage: ServingUsageRepository, private val complete: CompleteServingUsageService) {
    @Transactional
    operator fun invoke(request: CompleteGatewayUsageCommand) {
        val row = usage.findByIdOrNull(request.requestId) ?: throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_NOT_FOUND)
        val known = request.tokensKnown && request.inputTokens != null && request.outputTokens != null
        complete(ServingUsageObservation(
            AuthenticatedServingKey(row.keyId, row.accessId, row.clusterId, row.userId), row.model,
            row.startedAt.toInstant(ZoneOffset.UTC), Duration.between(row.startedAt, utcNow()).toMillis(),
            request.status.takeIf { it >= 100 } ?: 502, request.outcome,
            request.inputTokens.takeIf { known }, request.outputTokens.takeIf { known },
            row.model != null && !known, row.id, request.status.takeIf { it >= 100 },
        ))
    }
}
