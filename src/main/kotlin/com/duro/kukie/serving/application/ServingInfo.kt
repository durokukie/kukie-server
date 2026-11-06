package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ServingPortalProperties
import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.domain.ServingEndpointRepository
import org.springframework.stereotype.Component
import java.util.UUID
import java.time.Instant
import java.time.Duration
import java.time.ZoneOffset
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.domain.ConnectorModelStatuses
import com.duro.kukie.serving.domain.DisabledServingModels

@Component
class ServingInfo(private val endpoints: ServingEndpointRepository, private val relay: ServingRelayProperties, private val portal: ServingPortalProperties, private val connectors: ConnectorCredentials, private val statuses: ConnectorModelStatuses, private val disabled: DisabledServingModels) {
    fun of(clusterId: UUID, includeDisabled: Boolean = false): ServingInfoResponse {
        val credential = connectors.find(clusterId)
        if (credential != null) {
            val now = utcNow()
            val active = credential.expiresAt.isAfter(now)
            val observed = statuses.find(clusterId)?.takeIf { active && it.tokenHash == credential.tokenHash && it.receivedAt.isAfter(now.minusSeconds(45)) }
            val checked = observed?.checkedAt?.takeIf { it.isAfter(now.minusSeconds(45)) && !it.isAfter(now.plusSeconds(5)) }
            val connection = if (!active) "EXPIRED" else if (observed == null) "UNKNOWN" else if (!observed.connected) "OFFLINE" else "CONNECTED"
            val excluded = disabled.find(clusterId)
            val models = credential.models.filter { includeDisabled || it !in excluded }
            val states = models.map { name ->
                val state = when {
                    !active || connection == "OFFLINE" -> "UNAVAILABLE"
                    connection != "CONNECTED" || checked == null -> "UNKNOWN"
                    name in observed.readyModels -> "AVAILABLE"
                    else -> "UNAVAILABLE"
                }
                ServingModelStatus(name, state, name !in excluded)
            }
            return ServingInfoResponse(active, portal.publicApiBaseUrl?.takeIf { it.isNotBlank() }, models,
                ServingLimits(relay.maxConnectionsPerKey, relay.totalSeconds, relay.idleSeconds), "CONNECTOR", connection,
                checked?.toInstant(ZoneOffset.UTC), states,
                if (observed?.connected == true && checked != null) minOf(45 - Duration.between(checked, now).seconds.coerceAtLeast(0), 45 - Duration.between(observed.receivedAt, now).seconds.coerceAtLeast(0)).coerceIn(0, 45) else 0)
        }
        val endpoint = endpoints.findByClusterId(clusterId)
        val excluded = disabled.find(clusterId)
        val models = endpoint?.models.orEmpty().filter { includeDisabled || it !in excluded }
        return ServingInfoResponse(endpoint != null, portal.publicApiBaseUrl?.takeIf { it.isNotBlank() }, models,
            ServingLimits(relay.maxConnectionsPerKey, relay.totalSeconds, relay.idleSeconds), if (endpoint == null) "UNCONFIGURED" else "HTTPS_COUNTER",
            modelStatuses = models.map { ServingModelStatus(it, "UNKNOWN", it !in excluded) })
    }
}

data class ServingInfoResponse(val configured: Boolean, val apiBaseUrl: String?, val models: List<String>, val limits: ServingLimits, val connectionMode: String = "HTTPS_COUNTER", val connectionStatus: String = "UNKNOWN", val modelsCheckedAt: Instant? = null, val modelStatuses: List<ServingModelStatus> = models.map { ServingModelStatus(it, "UNKNOWN") }, val modelStatusValidForSeconds: Long = 0)
data class ServingModelStatus(val id: String, val state: String, val enabled: Boolean = true)
data class ServingLimits(val concurrentPerKeyPerInstance: Int, val totalSeconds: Long, val idleSeconds: Long, val maxKeys: Int = 5, val defaultExpiryDays: Int = 90, val maxExpiryDays: Int = 365, val tokenBudgetSupported: Boolean = false)
