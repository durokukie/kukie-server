package com.duro.kukie.chat.application

import com.duro.kukie.chat.application.port.`in`.CreateChatCommand
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.exception.ClusterRequiredException
import com.duro.kukie.chat.exception.ClusterTeamMismatchException
import com.duro.kukie.chat.presentation.dto.response.ChatResponse
import com.duro.kukie.chat.presentation.dto.response.CreateChatResponse
import com.duro.kukie.chat.presentation.dto.response.SessionResponse
import com.duro.kukie.cluster.application.ClusterPermission
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateChatService(
    private val chatSessionRepository: ChatSessionRepository,
    private val clusterPermission: ClusterPermission,
) {

    @Transactional
    operator fun invoke(command: CreateChatCommand): CreateChatResponse {
        val clusterId = command.clusterId ?: throw ClusterRequiredException()
        val cluster = clusterPermission.requireReadable(clusterId, command.userId)
        if (command.teamId != null && command.teamId != cluster.teamId) throw ClusterTeamMismatchException()

        val session = chatSessionRepository.save(
            ChatSession(
                userId = command.userId,
                clusterId = cluster.id,
                title = command.title,
                contextName = cluster.contextName,
                namespace = cluster.defaultNamespace,
                clusterFingerprint = cluster.fingerprint,
                visibility = command.visibility,
            ),
        )

        return CreateChatResponse(
            conversation = ChatResponse.of(session, cluster.teamId, running = false),
            session = SessionResponse.of(session, pending = emptyList()),
        )
    }
}
