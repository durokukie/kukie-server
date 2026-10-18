package com.duro.kukie.chat.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.domain.lockByIdOrThrow
import com.duro.kukie.chat.exception.ActiveRunExistsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeleteChatService(
    private val chatPermission: ChatPermission,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRunRepository: ChatRunRepository,
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(conversationId: UUID, userId: UUID) {
        // 요청 시작·갱신과 같은 순서(방 → run → 계획)로 방부터 잠가 그 사이 새 요청이 끼지 못하게 한다
        val session = chatSessionRepository.lockByIdOrThrow(conversationId)
        val access = chatPermission.requireReadable(session, userId)
        chatPermission.requireDeletable(access, userId)
        if (chatRunRepository.existsBySessionIdAndStatusIn(session.id, RunStatus.ACTIVE)) throw ActiveRunExistsException()

        actionPlanRepository.deleteAllBySessionId(session.id)
        chatRunRepository.deleteAllBySessionId(session.id)
        chatSessionRepository.delete(session)
    }
}
