package com.duro.kukie.serving.application

import com.duro.kukie.serving.application.port.`in`.UpdateServingModelAccessCommand
import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.domain.DisabledServingModels
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateServingModelAccessService(private val permission: ServingAccessPermission, private val credentials: ConnectorCredentials, private val disabled: DisabledServingModels) {
    @Transactional
    operator fun invoke(command: UpdateServingModelAccessCommand) {
        permission.requireAdmin(command.clusterId, command.userId)
        permission.lockCluster(command.clusterId)
        val credential = credentials.find(command.clusterId) ?: throw ServingAccessException(ServingErrorCode.SERVING_ENDPOINT_NOT_CONFIGURED)
        if (command.model !in credential.models) throw ServingAccessException(ServingErrorCode.SERVING_MODEL_NOT_REGISTERED)
        disabled.setEnabled(command.clusterId, command.model, command.enabled)
    }
}
