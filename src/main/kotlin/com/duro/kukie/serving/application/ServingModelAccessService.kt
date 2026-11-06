package com.duro.kukie.serving.application

import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.domain.DisabledServingModels
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ServingModelAccess(val id: String, val enabled: Boolean)

@Service
class ServingModelAccessService(private val permission: ServingAccessPermission, private val credentials: ConnectorCredentials, private val disabled: DisabledServingModels) {
    @Transactional(readOnly = true)
    fun list(clusterId: UUID, userId: UUID): List<ServingModelAccess> {
        permission.requireAdmin(clusterId, userId)
        val excluded = disabled.find(clusterId)
        return credentials.find(clusterId)?.models.orEmpty().map { ServingModelAccess(it, it !in excluded) }
    }

    @Transactional
    fun update(clusterId: UUID, userId: UUID, model: String, enabled: Boolean) {
        permission.requireAdmin(clusterId, userId)
        permission.lockCluster(clusterId)
        val credential = credentials.find(clusterId) ?: throw ServingAccessException(ServingErrorCode.SERVING_ENDPOINT_NOT_CONFIGURED)
        if (model !in credential.models) throw ServingAccessException(ServingErrorCode.SERVING_MODEL_NOT_REGISTERED)
        disabled.setEnabled(clusterId, model, enabled)
    }
}
