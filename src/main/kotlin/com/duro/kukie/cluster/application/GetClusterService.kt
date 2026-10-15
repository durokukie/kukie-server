package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetClusterService(
    private val clusterPermission: ClusterPermission,
) {

    @Transactional(readOnly = true)
    operator fun invoke(clusterId: UUID, userId: UUID): ClusterResponse =
        ClusterResponse.from(clusterPermission.requireReadable(clusterId, userId))
}
