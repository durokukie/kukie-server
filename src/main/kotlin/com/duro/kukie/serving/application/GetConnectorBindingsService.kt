package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

data class ConnectorBinding(val clusterId: UUID, val tokenSha256: String, val expiresAt: Instant)

@Service
class GetConnectorBindingsService(private val credentials: ConnectorCredentials) {
    @Transactional(readOnly = true)
    operator fun invoke(): Map<String, List<ConnectorBinding>> {
        val rows = credentials.active(utcNow())
        if (rows.size > 256) throw ServingAccessException(ServingErrorCode.SERVING_RELAY_BUSY)
        return mapOf("bindings" to rows.map { ConnectorBinding(it.clusterId, it.tokenHash, it.expiresAt.toInstant(ZoneOffset.UTC)) })
    }
}
