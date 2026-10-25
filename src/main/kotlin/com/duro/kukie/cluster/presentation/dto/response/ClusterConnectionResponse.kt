package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.domain.Cluster
import java.util.UUID

data class ClusterConnectionResponse(
    val clusterId: UUID,
    val name: String,
    val apiServer: String,
    val caData: String?,
    val insecure: Boolean,
    val contextName: String,
    val namespace: String,
    val fingerprint: String,
    val hasGpu: Boolean?,
    val credential: Map<String, String>,
) {
    companion object {
        fun of(cluster: Cluster, credential: Map<String, String>) = ClusterConnectionResponse(
            clusterId = cluster.id,
            name = cluster.name,
            apiServer = cluster.apiServer,
            caData = cluster.caData,
            insecure = cluster.insecure,
            contextName = cluster.contextName,
            namespace = cluster.defaultNamespace,
            fingerprint = cluster.fingerprint,
            hasGpu = cluster.hasGpu,
            credential = credential,
        )
    }
}
