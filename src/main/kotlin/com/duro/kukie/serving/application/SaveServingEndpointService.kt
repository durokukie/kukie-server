package com.duro.kukie.serving.application

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.serving.application.port.`in`.ConfigureServingEndpointCommand
import com.duro.kukie.serving.domain.ServingEndpoint
import com.duro.kukie.serving.domain.ServingEndpointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SaveServingEndpointService(
    private val permission: ServingAccessPermission,
    private val endpoints: ServingEndpointRepository,
    private val cipher: CredentialCipher,
) {
    @Transactional
    operator fun invoke(command: ConfigureServingEndpointCommand, validatedUrl: String) {
        permission.lockCluster(command.clusterId)
        permission.requireAdmin(command.clusterId, command.userId)
        val token = cipher.encrypt(mapOf("token" to command.serviceToken))
        val existing = endpoints.findByClusterId(command.clusterId)
        if (existing == null) endpoints.save(ServingEndpoint(command.clusterId, validatedUrl, token, command.models.distinct()))
        else existing.configure(validatedUrl, token, command.models.distinct())
    }
}
