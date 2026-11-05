package com.duro.kukie.serving.application

import com.duro.kukie.serving.domain.ServingEndpointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetServingEndpointService(private val permission: ServingAccessPermission, private val endpoints: ServingEndpointRepository) {
    @Transactional(readOnly = true)
    operator fun invoke(clusterId: UUID, userId: UUID): ServingEndpointResponse? {
        permission.requireAdmin(clusterId, userId)
        return endpoints.findByClusterId(clusterId)?.let { ServingEndpointResponse(it.baseUrl, it.models) }
    }
}

data class ServingEndpointResponse(val baseUrl: String, val models: List<String>)
