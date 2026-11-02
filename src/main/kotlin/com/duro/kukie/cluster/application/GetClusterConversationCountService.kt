package com.duro.kukie.cluster.application

import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.cluster.presentation.dto.response.ClusterConversationCountResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetClusterConversationCountService(
    private val clusterPermission: ClusterPermission,
    private val chatSessionRepository: ChatSessionRepository,
) {

    operator fun invoke(clusterId: UUID, userId: UUID): ClusterConversationCountResponse {
        val cluster = clusterPermission.requireWritable(clusterId, userId)

        return ClusterConversationCountResponse(chatSessionRepository.countByClusterId(cluster.id))
    }
}
