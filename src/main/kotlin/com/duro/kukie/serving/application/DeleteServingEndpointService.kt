package com.duro.kukie.serving.application

import com.duro.kukie.serving.domain.ServingEndpointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeleteServingEndpointService(private val permission: ServingAccessPermission, private val endpoints: ServingEndpointRepository) {
    @Transactional
    operator fun invoke(clusterId: UUID, userId: UUID) {
        permission.lockCluster(clusterId)
        permission.requireAdmin(clusterId, userId)
        endpoints.findByClusterId(clusterId)?.let(endpoints::delete)
    }
}
