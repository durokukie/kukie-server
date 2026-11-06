package com.duro.kukie.serving.application

import com.duro.kukie.cluster.application.EksCredential
import com.duro.kukie.cluster.application.EksGpuAccess
import com.duro.kukie.cluster.application.CreateServedModelService
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.application.port.out.NamedServingModel
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.Semaphore

@Component
class EksServingResolver(private val clusters: ClusterRepository, private val access: EksGpuAccess, private val gateway: EksAccessGateway, properties: ServingRelayProperties) {
    private val slots = Semaphore(properties.maxConnections)

    fun resolve(clusterId: UUID): EksServingTarget? {
        val cluster = clusters.findByIdOrNull(clusterId) ?: return null
        if (cluster.provider != ClusterProvider.EKS) return null
        if (cluster.accessLevel != AccessLevel.FULL || cluster.accessLevelFailedAt != null) return null
        val credential = try { access.of(cluster).takeIf { it.serving }?.credential ?: return null }
        catch (_: Exception) { throw ServingAccessException(ServingErrorCode.SERVING_UPSTREAM_FAILED) }
        val endpoint = access.endpointOf(cluster)
        if (!slots.tryAcquire()) throw ServingAccessException(ServingErrorCode.SERVING_RELAY_BUSY)
        try {
            val models = gateway.listServingModels(credential.target, credential.region, credential.clusterName, endpoint, CreateServedModelService.SERVING_NAMESPACE)
                .filter { !it.state.deleting }.associateBy { it.name }
            return EksServingTarget(credential, endpoint, models)
        } catch (_: Exception) { throw ServingAccessException(ServingErrorCode.SERVING_UPSTREAM_FAILED) }
        finally { slots.release() }
    }
}

class EksServingTarget(val credential: EksCredential, val endpoint: EksClusterEndpoint, val models: Map<String, NamedServingModel>) {
    val readyModels: List<String> get() = models.values.filter { it.state.phase == "Ready" }.map { it.name }.sorted()
    override fun toString() = "EksServingTarget([REDACTED])"
}
