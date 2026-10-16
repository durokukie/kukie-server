package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.domain.ClusterRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeleteClusterService(
    private val clusterRepository: ClusterRepository,
    private val clusterPermission: ClusterPermission,
) {

    @Transactional
    operator fun invoke(clusterId: UUID, userId: UUID) {
        val cluster = clusterPermission.requireWritable(clusterId, userId)

        clusterRepository.delete(cluster)
    }
}
