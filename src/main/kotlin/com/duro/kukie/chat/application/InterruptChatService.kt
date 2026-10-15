package com.duro.kukie.chat.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.closeOpenPlans
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.chat.application.port.`in`.InterruptChatCommand
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.exception.ChatNotFoundException
import com.duro.kukie.chat.presentation.dto.response.InterruptRunsResponse
import com.duro.kukie.chat.presentation.dto.response.RunResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InterruptChatService(
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRunRepository: ChatRunRepository,
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(command: InterruptChatCommand): InterruptRunsResponse {
        if (!chatSessionRepository.existsById(command.conversationId)) throw ChatNotFoundException()

        val runs = chatRunRepository.lockAllBySessionIdAndStatusIn(command.conversationId, RunStatus.ACTIVE)
        val closedPlans = runs.flatMap { run ->
            run.interrupt(command.message)
            actionPlanRepository.closeOpenPlans(run.id)
        }
        chatRunRepository.flush()

        return InterruptRunsResponse(runs.map(RunResponse::from), closedPlans.map(PlanResponse::from))
    }
}
