package com.duro.kukie.serving.application

import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BeginGatewayUsageService(private val permission: GatewayKeyPermission, private val begin: BeginServingUsageService) {
    @Transactional
    operator fun invoke(keyId: UUID, model: String?): UUID {
        val identity = permission.requireUsable(keyId)
        if (model != null && model !in permission.models(identity.clusterId)) throw ServingAccessException(ServingErrorCode.SERVING_MODEL_DENIED)
        return begin(identity, model)
    }
}
