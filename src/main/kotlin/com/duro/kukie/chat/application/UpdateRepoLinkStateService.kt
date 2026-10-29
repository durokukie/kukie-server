package com.duro.kukie.chat.application

import com.duro.kukie.chat.application.port.`in`.UpdateRepoLinkStateCommand
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.exception.ChatNotFoundException
import com.duro.kukie.chat.presentation.dto.response.RepoLinkStateResponse
import com.duro.kukie.global.util.withoutNul
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 숨은 칸을 통째로 덮어쓴다. */
@Service
class UpdateRepoLinkStateService(
    private val chatSessionRepository: ChatSessionRepository,
) {

    @Transactional
    operator fun invoke(command: UpdateRepoLinkStateCommand): RepoLinkStateResponse {
        val state = command.state.withoutNul()
        if (chatSessionRepository.updateRepoLinkState(command.conversationId, state) == 0) throw ChatNotFoundException()

        return RepoLinkStateResponse(state)
    }
}
