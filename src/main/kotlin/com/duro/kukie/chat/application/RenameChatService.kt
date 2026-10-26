package com.duro.kukie.chat.application

import com.duro.kukie.chat.application.port.`in`.RenameChatCommand
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.domain.findByIdOrThrow
import com.duro.kukie.chat.domain.lockByIdOrThrow
import com.duro.kukie.chat.presentation.dto.response.ChatResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RenameChatService(
    private val chatPermission: ChatPermission,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRunRepository: ChatRunRepository,
) {

    @Transactional
    operator fun invoke(command: RenameChatCommand): ChatResponse {
        val session = chatSessionRepository.lockByIdOrThrow(command.conversationId)
        val access = chatPermission.requireReadable(session, command.userId)
        chatPermission.requireRenamable(access, command.userId)

        chatSessionRepository.updateTitle(session.id, command.title)
        val renamed = chatSessionRepository.findByIdOrThrow(session.id)
        val running = chatRunRepository.existsBySessionIdAndStatusIn(session.id, listOf(RunStatus.RUNNING))

        return ChatResponse.of(renamed, access.teamId, running = running)
    }
}
