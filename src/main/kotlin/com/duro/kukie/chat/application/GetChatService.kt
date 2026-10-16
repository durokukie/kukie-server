package com.duro.kukie.chat.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.domain.RunTurn
import com.duro.kukie.chat.presentation.dto.response.ChatDetailResponse
import com.duro.kukie.chat.presentation.dto.response.ChatResponse
import com.duro.kukie.chat.presentation.dto.response.SessionResponse
import com.duro.kukie.chat.presentation.dto.response.TurnResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetChatService(
    private val chatPermission: ChatPermission,
    private val chatRunRepository: ChatRunRepository,
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(conversationId: UUID, userId: UUID): ChatDetailResponse {
        val access = chatPermission.requireReadable(conversationId, userId)
        val session = access.session
        val turns = chatRunRepository.findTurnsBySessionId(session.id)
        val active = turns.firstOrNull { it.status.active }

        return ChatDetailResponse(
            conversation = ChatResponse.of(session, access.teamId, running = active?.status == RunStatus.RUNNING),
            session = SessionResponse.of(session, pending = pendingCalls(active)),
            turns = turns.map(TurnResponse::from),
        )
    }

    private fun pendingCalls(active: RunTurn?): List<String> {
        if (active == null || active.status !in PENDING_RUN) return emptyList()

        return actionPlanRepository.findAllByRunIdAndStatusInOrderByCreatedAtAscIdAsc(active.id, PENDING_PLAN)
            .map { it.toolCallId }
    }

    companion object {
        private val PENDING_RUN = setOf(RunStatus.AWAITING_APPROVAL, RunStatus.RECOVERY_REQUIRED)
        private val PENDING_PLAN = setOf(
            PlanStatus.WAITING_APPROVAL,
            PlanStatus.APPROVED,
            PlanStatus.REJECTED,
            PlanStatus.EXECUTING,
        )
    }
}
