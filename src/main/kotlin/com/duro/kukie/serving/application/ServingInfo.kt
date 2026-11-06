package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ServingPortalProperties
import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.domain.ServingEndpointRepository
import org.springframework.stereotype.Component
import java.util.UUID
import java.time.Instant
import java.time.ZoneOffset
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.domain.ConnectorModelStatuses

@Component
class ServingInfo(private val endpoints: ServingEndpointRepository, private val relay: ServingRelayProperties, private val portal: ServingPortalProperties, private val connectors: ConnectorCredentials, private val statuses: ConnectorModelStatuses) {
    fun of(clusterId: UUID): ServingInfoResponse {
        val credential = connectors.find(clusterId)
        if (credential != null) {
            val now = utcNow()
            val active = credential.expiresAt.isAfter(now)
            val observed = statuses.find(clusterId)?.takeIf { active && it.tokenHash == credential.tokenHash && it.receivedAt.isAfter(now.minusSeconds(45)) }
            val checked = observed?.checkedAt?.takeIf { it.isAfter(now.minusSeconds(45)) && !it.isAfter(now.plusSeconds(5)) }
            val connection = if (!active) "EXPIRED" else if (observed == null) "UNKNOWN" else if (!observed.connected) "OFFLINE" else "CONNECTED"
            val states = credential.models.map { name ->
                val state = when {
                    !active || connection == "OFFLINE" -> "UNAVAILABLE"
                    connection != "CONNECTED" || checked == null -> "UNKNOWN"
                    name in observed.readyModels -> "AVAILABLE"
                    else -> "UNAVAILABLE"
                }
                ServingModelStatus(name, state)
            }
            return ServingInfoResponse(active, portal.publicApiBaseUrl?.takeIf { it.isNotBlank() }, credential.models,
                ServingLimits(relay.maxConnectionsPerKey, relay.totalSeconds, relay.idleSeconds), "CONNECTOR", connection,
                checked?.toInstant(ZoneOffset.UTC), states)
        }
        val endpoint = endpoints.findByClusterId(clusterId)
        return ServingInfoResponse(endpoint != null, portal.publicApiBaseUrl?.takeIf { it.isNotBlank() }, endpoint?.models ?: emptyList(), ServingLimits(relay.maxConnectionsPerKey, relay.totalSeconds, relay.idleSeconds), if (endpoint == null) "UNCONFIGURED" else "HTTPS_COUNTER")
    }
}

data class ServingInfoResponse(val configured: Boolean, val apiBaseUrl: String?, val models: List<String>, val limits: ServingLimits, val connectionMode: String = "HTTPS_COUNTER", val connectionStatus: String = "UNKNOWN", val modelsCheckedAt: Instant? = null, val modelStatuses: List<ServingModelStatus> = models.map { ServingModelStatus(it, "UNKNOWN") })
data class ServingModelStatus(val id: String, val state: String)
data class ServingLimits(val concurrentPerKeyPerInstance: Int, val totalSeconds: Long, val idleSeconds: Long, val maxKeys: Int = 5, val defaultExpiryDays: Int = 90, val maxExpiryDays: Int = 365, val tokenBudgetSupported: Boolean = false)
