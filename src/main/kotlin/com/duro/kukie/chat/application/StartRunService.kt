package com.duro.kukie.chat.application

import com.duro.kukie.chat.application.port.`in`.StartRunCommand
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.domain.flushOrThrowActiveRunExists
import com.duro.kukie.chat.domain.lockByIdOrThrow
import com.duro.kukie.chat.exception.ActiveRunExistsException
import com.duro.kukie.chat.exception.RequestMismatchException
import com.duro.kukie.chat.presentation.dto.response.RunResponse
import com.duro.kukie.chat.presentation.dto.response.StartRunResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StartRunService(
    private val chatPermission: ChatPermission,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRunRepository: ChatRunRepository,
) {

    @Transactional
    operator fun invoke(command: StartRunCommand): StartRunResponse {
        val session = chatSessionRepository.lockByIdOrThrow(command.conversationId)
        chatPermission.requireReadable(session, command.userId)

        chatRunRepository.findBySessionIdAndRequestId(session.id, command.requestId)?.let { existing ->
            if (!existing.isReplayOf(command.kind, command.inputText)) throw RequestMismatchException()
            return StartRunResponse(RunResponse.from(existing), created = false)
        }
        if (chatRunRepository.existsBySessionIdAndStatusIn(session.id, RunStatus.ACTIVE)) throw ActiveRunExistsException()

        val run = chatRunRepository.save(
            ChatRun(
                sessionId = session.id,
                requestId = command.requestId,
                turnNo = (chatRunRepository.findMaxTurnNo(session.id) ?: 0) + 1,
                kind = command.kind,
                mode = command.mode,
                inputText = command.inputText,
            ),
        )
        session.touch()
        chatRunRepository.flushOrThrowActiveRunExists()

        return StartRunResponse(RunResponse.from(run), created = true)
    }
}
