package com.duro.kukie.chat.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.presentation.dto.response.ChatContextResponse
import com.duro.kukie.chat.presentation.dto.response.RunResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetChatContextService(
    private val chatPermission: ChatPermission,
    private val chatRunRepository: ChatRunRepository,
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(conversationId: UUID, userId: UUID, purpose: ContextPurpose): ChatContextResponse {
        val access = chatPermission.requireReadable(conversationId, userId)
        if (purpose == ContextPurpose.APPROVE) chatPermission.requireApprover(access, userId)

        val sessionId = access.session.id
        val activeRun = chatRunRepository.findFirstBySessionIdAndStatusIn(sessionId, RunStatus.ACTIVE)
        val activeRunPlans = activeRun?.let { actionPlanRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(it.id) }.orEmpty()
        val history = chatRunRepository
            .findTop100BySessionIdAndStatusAndAgentMessagesIsNotNullOrderByTurnNoDesc(sessionId, RunStatus.COMPLETED)
            .reversed()

        return ChatContextResponse(
            userId = userId,
            conversation = ChatContextResponse.Chat.from(access),
            activeRun = activeRun?.let(RunResponse::from),
            activeRunPlans = activeRunPlans.map(PlanResponse::from),
            history = history.map(ChatContextResponse.History::from),
        )
    }
}
