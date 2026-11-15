package com.duro.kukie.serving.application

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.serving.application.port.`in`.ConfigureServingEndpointCommand
import com.duro.kukie.serving.infrastructure.ServingDestinationPolicy
import org.springframework.stereotype.Service

@Service
class ConfigureServingEndpointService(
    private val permission: ServingAccessPermission,
    private val policy: ServingDestinationPolicy,
    private val saveEndpoint: SaveServingEndpointService,
) {
    operator fun invoke(command: ConfigureServingEndpointCommand) {
        permission.requireAdmin(command.clusterId, command.userId)
        if (command.models.isEmpty() || command.models.size > 100 || command.models.any { !MODEL.matches(it) } ||
            command.serviceToken.length !in 1..4096 || command.serviceToken.any { it.code !in 33..126 }) throw BusinessException(GlobalErrorCode.BAD_REQUEST)
        val validatedUrl = policy.validate(command.baseUrl)
        saveEndpoint(command, validatedUrl)
    }

    companion object { val MODEL = Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,199}") }
}
