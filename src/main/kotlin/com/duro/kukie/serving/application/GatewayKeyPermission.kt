package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.*
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GatewayKeyPermission(private val keys: ServingApiKeyRepository, private val accesses: ServingAccessRepository, private val endpoints: ServingEndpointRepository, private val connectors: ConnectorCredentials, private val disabled: DisabledServingModels) {
    fun requireUsable(keyId: UUID): AuthenticatedServingKey {
        val key = keys.findByIdOrNull(keyId)?.takeIf { it.isUsable(utcNow()) } ?: invalid()
        val access = accesses.findByIdOrNull(key.accessId)?.takeIf { it.status == ServingAccessStatus.ACTIVE } ?: invalid()
        return AuthenticatedServingKey(key.id, access.id, access.clusterId, access.userId ?: invalid())
    }

    fun models(clusterId: UUID): List<String> {
        val connector = connectors.find(clusterId)
        val excluded = disabled.find(clusterId)
        if (connector != null) return connector.models.filterNot { it in excluded }.takeIf { connector.expiresAt.isAfter(utcNow()) }.orEmpty()
        return endpoints.findByClusterId(clusterId)?.models?.filterNot { it in excluded }.orEmpty()
    }

    private fun invalid(): Nothing = throw ServingAccessException(ServingErrorCode.SERVING_KEY_INVALID)
}
