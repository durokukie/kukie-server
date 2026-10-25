package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.UpdateClusterGpuCommand
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateClusterGpuService(
    private val clusterRepository: ClusterRepository,
) {

    @Transactional
    operator fun invoke(command: UpdateClusterGpuCommand): ClusterResponse {
        val cluster = clusterRepository.findByIdOrThrow(command.clusterId)
        cluster.recordGpu(command.hasGpu)
        clusterRepository.flush()

        return ClusterResponse.from(cluster)
    }
}
