package com.duro.kukie.chat.application

import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.findByIdOrThrow
import com.duro.kukie.chat.presentation.dto.response.RepoLinkStateResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetRepoLinkStateService(
    private val chatSessionRepository: ChatSessionRepository,
) {

    operator fun invoke(conversationId: UUID): RepoLinkStateResponse =
        RepoLinkStateResponse(chatSessionRepository.findByIdOrThrow(conversationId).repoLinkState)
}
