package com.duro.kukie.cluster

import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.util.Base64
import java.util.UUID

object ClusterFixture {

    const val DEFAULT_NAME = "운영"
    const val DEFAULT_SERVER = "https://api.example.com"
    const val DEFAULT_CONTEXT = "prod"
    const val DEFAULT_NAMESPACE = "web"
    const val DEFAULT_TOKEN = "secret-token"
    val CA: String = Base64.getEncoder().encodeToString("fake-ca".toByteArray())

    fun kubeconfig(
        serverUrl: String = DEFAULT_SERVER,
        ca: String? = CA,
        user: Map<String, Any?> = mapOf("token" to DEFAULT_TOKEN),
        context: String = DEFAULT_CONTEXT,
        extraContext: String? = null,
        namespace: String = DEFAULT_NAMESPACE,
        cluster: Map<String, Any?>? = null,
    ): String {
        val contexts = mutableListOf<Map<String, Any?>>(
            mapOf("name" to context, "context" to mapOf("cluster" to "c", "user" to "u", "namespace" to namespace)),
        )
        if (extraContext != null) {
            contexts += mapOf("name" to extraContext, "context" to mapOf("cluster" to "c", "user" to "u"))
        }
        val document = linkedMapOf<String, Any?>(
            "apiVersion" to "v1",
            "kind" to "Config",
            "clusters" to listOf(
                mapOf("name" to "c", "cluster" to (cluster ?: mapOf("server" to serverUrl, "certificate-authority-data" to ca))),
            ),
            "users" to listOf(mapOf("name" to "u", "user" to user)),
            "contexts" to contexts,
        )
        if (extraContext == null) document["current-context"] = context

        return Yaml(DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK }).dump(document)
    }

    fun cluster(
        registeredBy: UUID,
        teamId: UUID,
        name: String = DEFAULT_NAME,
        apiServer: String = DEFAULT_SERVER,
        credential: String = "암호문",
        fingerprint: String = "fp",
        provider: ClusterProvider = ClusterProvider.GENERIC,
    ): Cluster = Cluster(
        teamId = teamId,
        registeredBy = registeredBy,
        name = name,
        apiServer = apiServer,
        caData = CA,
        insecure = false,
        credential = credential,
        contextName = DEFAULT_CONTEXT,
        defaultNamespace = DEFAULT_NAMESPACE,
        fingerprint = fingerprint,
        provider = provider,
    )
}
