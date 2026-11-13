package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.UpdateClusterStatusCommand
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.lockByIdOrThrow
import com.duro.kukie.cluster.exception.ClusterChangedException
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateClusterStatusService(
    private val clusterRepository: ClusterRepository,
) {

    @Transactional
    operator fun invoke(command: UpdateClusterStatusCommand): ClusterResponse {
        // 확인하는 동안 kubeconfig 가 바뀌었으면 옛 연결의 결과다 — 잠근 뒤 지문을 대조한다 (GPU 여부와 같은 규칙)
        val cluster = clusterRepository.lockByIdOrThrow(command.clusterId)
        if (cluster.fingerprint != command.fingerprint) throw ClusterChangedException()
        cluster.recordCheck(command.status, command.checkedAt)
        clusterRepository.flush()

        return ClusterResponse.from(cluster)
    }
}
