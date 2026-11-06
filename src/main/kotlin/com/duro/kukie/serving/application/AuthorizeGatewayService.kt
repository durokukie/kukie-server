package com.duro.kukie.serving.application

import com.duro.kukie.serving.presentation.dto.request.GatewayAuthorizeRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuthorizeGatewayService(private val authenticate: AuthenticateServingApiKeyService, private val permission: GatewayKeyPermission) {
    @Transactional(readOnly = true)
    operator fun invoke(request: GatewayAuthorizeRequest): GatewayIdentity {
        val identity = authenticate(request.token)
        return GatewayIdentity(identity.keyId, identity.clusterId, permission.models(identity.clusterId))
    }
}

data class GatewayIdentity(val keyId: UUID, val clusterId: UUID, val models: List<String>)
