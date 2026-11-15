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

data class ConnectorBinding(
    val clusterId: UUID,
    val tokenSha256: String,
    val expiresAt: Instant,
    val previousTokenSha256: String? = null,
    val previousExpiresAt: Instant? = null,
)

@Service
class GetConnectorBindingsService(private val credentials: ConnectorCredentials) {
    @Transactional(readOnly = true)
    operator fun invoke(): Map<String, List<ConnectorBinding>> {
        val now = utcNow()
        val rows = credentials.active(now)
        if (rows.size > 256) throw ServingAccessException(ServingErrorCode.SERVING_CONNECTOR_REGISTRY_INVALID)
        return mapOf("bindings" to rows.map {
            val previous = it.previousActive(now)
            ConnectorBinding(it.clusterId, it.tokenHash, it.expiresAt.toInstant(ZoneOffset.UTC),
                if (previous) it.previousTokenHash else null, if (previous) it.previousExpiresAt!!.toInstant(ZoneOffset.UTC) else null)
        })
    }
}
