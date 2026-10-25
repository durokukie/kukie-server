package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.UpdateClusterGpuCommand
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.lockByIdOrThrow
import com.duro.kukie.cluster.exception.ClusterChangedException
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateClusterGpuService(
    private val clusterRepository: ClusterRepository,
) {

    @Transactional
    operator fun invoke(command: UpdateClusterGpuCommand): ClusterResponse {
        // 읽는 동안 kubeconfig 가 바뀌었으면 옛 클러스터의 값이다 — 잠근 뒤 지문을 대조한다
        val cluster = clusterRepository.lockByIdOrThrow(command.clusterId)
        if (cluster.fingerprint != command.fingerprint) throw ClusterChangedException()
        cluster.recordGpu(command.hasGpu)
        clusterRepository.flush()

        return ClusterResponse.from(cluster)
    }
}
