package com.duro.kukie.chat.application

import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.presentation.dto.response.ChatResponse
import com.duro.kukie.cluster.domain.ClusterRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetChatsService(
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRunRepository: ChatRunRepository,
    private val clusterRepository: ClusterRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID, clusterId: UUID?): List<ChatResponse> {
        val sessions = chatSessionRepository.findAllVisibleTo(userId, clusterId)
        if (sessions.isEmpty()) return emptyList()

        val teams = clusterRepository.findAllById(sessions.map { it.clusterId }.toSet()).associate { it.id to it.teamId }
        val running = chatRunRepository.findSessionIdsWithStatus(sessions.map { it.id }, RunStatus.RUNNING).toSet()

        return sessions.map { ChatResponse.of(it, teams.getValue(it.clusterId), running = it.id in running) }
    }
}
