package com.duro.kukie.serving.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuthorizeGatewayService(private val authenticate: AuthenticateServingApiKeyService, private val permission: GatewayKeyPermission) {
    @Transactional(readOnly = true)
    operator fun invoke(token: String): GatewayIdentity {
        val identity = authenticate(token)
        return GatewayIdentity(identity.keyId, identity.clusterId, permission.models(identity.clusterId))
    }
}

data class GatewayIdentity(val keyId: UUID, val clusterId: UUID, val models: List<String>)
