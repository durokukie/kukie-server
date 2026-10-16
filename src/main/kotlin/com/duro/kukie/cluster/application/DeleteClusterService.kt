package com.duro.kukie.cluster.application

import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.ClusterInUseException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeleteClusterService(
    private val clusterRepository: ClusterRepository,
    private val clusterPermission: ClusterPermission,
    private val chatSessionRepository: ChatSessionRepository,
) {

    @Transactional
    operator fun invoke(clusterId: UUID, userId: UUID) {
        val cluster = clusterPermission.requireWritable(clusterId, userId)
        if (chatSessionRepository.existsByClusterId(cluster.id)) throw ClusterInUseException()

        clusterRepository.delete(cluster)
    }
}
