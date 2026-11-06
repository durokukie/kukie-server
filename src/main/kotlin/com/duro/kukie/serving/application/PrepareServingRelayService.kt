package com.duro.kukie.serving.application

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.serving.application.port.out.CounterTarget
import com.duro.kukie.serving.domain.ServingEndpointRepository
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Service

@Service
class PrepareServingRelayService(
    private val authenticate: AuthenticateServingApiKeyService,
    private val endpoints: ServingEndpointRepository,
    private val cipher: CredentialCipher,
    private val eks: EksServingResolver,
) {
    operator fun invoke(token: String): ServingRelayTarget {
        val identity = authenticate(token)
        val endpoint = endpoints.findByClusterId(identity.clusterId)
        if (endpoint == null) {
            val target = eks.resolve(identity.clusterId) ?: throw ServingAccessException(ServingErrorCode.SERVING_ENDPOINT_NOT_CONFIGURED)
            return ServingRelayTarget(identity, null, target.models.keys.toList(), target)
        }
        val secret = try { cipher.decrypt(endpoint.encryptedToken)["token"] ?: failed() } catch (_: Exception) { failed() }
        return ServingRelayTarget(identity, CounterTarget(endpoint.baseUrl, secret), endpoint.models.toList())
    }

    private fun failed(): Nothing = throw ServingAccessException(ServingErrorCode.SERVING_UPSTREAM_FAILED)
}

class ServingRelayTarget(val identity: AuthenticatedServingKey, val counter: CounterTarget?, val models: List<String>, val eks: EksServingTarget? = null) {
    override fun toString() = "ServingRelayTarget(identity=$identity)"
}
