package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ServingPortalProperties
import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.domain.ServingEndpointRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ServingInfo(private val endpoints: ServingEndpointRepository, private val relay: ServingRelayProperties, private val portal: ServingPortalProperties) {
    fun of(clusterId: UUID): ServingInfoResponse {
        val endpoint = endpoints.findByClusterId(clusterId)
        return ServingInfoResponse(endpoint != null, portal.publicApiBaseUrl?.takeIf { it.isNotBlank() }, endpoint?.models ?: emptyList(), ServingLimits(relay.maxConnectionsPerKey, relay.totalSeconds, relay.idleSeconds))
    }
}

data class ServingInfoResponse(val configured: Boolean, val apiBaseUrl: String?, val models: List<String>, val limits: ServingLimits)
data class ServingLimits(val concurrentPerKeyPerInstance: Int, val totalSeconds: Long, val idleSeconds: Long, val maxKeys: Int = 5, val defaultExpiryDays: Int = 90, val maxExpiryDays: Int = 365, val tokenBudgetSupported: Boolean = false)
