package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.*
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GatewayKeyPermission(private val endpoints: ServingEndpointRepository, private val connectors: ConnectorCredentials, private val disabled: DisabledServingModels) {
    fun models(clusterId: UUID): List<String> {
        val connector = connectors.find(clusterId)
        val excluded = disabled.find(clusterId)
        if (connector != null) return connector.models.filterNot { it in excluded }.takeIf { connector.expiresAt.isAfter(utcNow()) }.orEmpty()
        return endpoints.findByClusterId(clusterId)?.models?.filterNot { it in excluded }.orEmpty()
    }
}
