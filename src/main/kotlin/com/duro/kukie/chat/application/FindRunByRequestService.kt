package com.duro.kukie.chat.application

import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.exception.RunNotFoundException
import com.duro.kukie.chat.presentation.dto.response.RunResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FindRunByRequestService(
    private val chatPermission: ChatPermission,
    private val chatRunRepository: ChatRunRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(conversationId: UUID, userId: UUID, requestId: String): RunResponse {
        val session = chatPermission.requireReadable(conversationId, userId).session
        val run = chatRunRepository.findBySessionIdAndRequestId(session.id, requestId) ?: throw RunNotFoundException()

        return RunResponse.from(run)
    }
}
