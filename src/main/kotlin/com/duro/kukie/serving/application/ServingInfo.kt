package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ServingPortalProperties
import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.domain.ServingEndpointRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ServingInfo(private val endpoints: ServingEndpointRepository, private val relay: ServingRelayProperties, private val portal: ServingPortalProperties, private val eks: EksServingResolver) {
    fun of(clusterId: UUID): ServingInfoResponse {
        val endpoint = endpoints.findByClusterId(clusterId)
        val automatic = if (endpoint == null) eks.resolve(clusterId) else null
        val mode = if (endpoint != null) "HTTPS_COUNTER" else if (automatic != null) "EKS_PROXY" else "UNCONFIGURED"
        return ServingInfoResponse(endpoint != null || automatic != null, portal.publicApiBaseUrl?.takeIf { it.isNotBlank() }, endpoint?.models ?: automatic?.readyModels ?: emptyList(), ServingLimits(relay.maxConnectionsPerKey, relay.totalSeconds, relay.idleSeconds), mode)
    }
}

data class ServingInfoResponse(val configured: Boolean, val apiBaseUrl: String?, val models: List<String>, val limits: ServingLimits, val connectionMode: String = "HTTPS_COUNTER")
data class ServingLimits(val concurrentPerKeyPerInstance: Int, val totalSeconds: Long, val idleSeconds: Long, val maxKeys: Int = 5, val defaultExpiryDays: Int = 90, val maxExpiryDays: Int = 365, val tokenBudgetSupported: Boolean = false)
