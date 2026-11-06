package com.duro.kukie.serving.application

import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.domain.DisabledServingModels
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ServingModelAccess(val id: String, val enabled: Boolean)

@Service
class GetServingModelAccessService(private val permission: ServingAccessPermission, private val credentials: ConnectorCredentials, private val disabled: DisabledServingModels) {
    @Transactional(readOnly = true)
    operator fun invoke(clusterId: UUID, userId: UUID): List<ServingModelAccess> {
        permission.requireAdmin(clusterId, userId)
        val excluded = disabled.find(clusterId)
        return credentials.find(clusterId)?.models.orEmpty().map { ServingModelAccess(it, it !in excluded) }
    }

}
