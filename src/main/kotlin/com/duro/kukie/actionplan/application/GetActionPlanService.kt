package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.application.port.out.ActionPlanMarkdownRenderer
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.findByIdOrThrow
import com.duro.kukie.actionplan.exception.ActionPlanNotFoundException
import com.duro.kukie.actionplan.presentation.dto.response.ActionPlanDetailResponse
import com.duro.kukie.chat.application.ChatPermission
import com.duro.kukie.chat.domain.ChatSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetActionPlanService(
    private val actionPlanRepository: ActionPlanRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatPermission: ChatPermission,
    private val actionPlanMarkdownRenderer: ActionPlanMarkdownRenderer,
) {

    @Transactional(readOnly = true)
    operator fun invoke(planId: UUID, userId: UUID): ActionPlanDetailResponse {
        val plan = actionPlanRepository.findByIdOrThrow(planId)
        val session = chatSessionRepository.findByRunId(plan.runId) ?: throw ActionPlanNotFoundException()
        if (!chatPermission.isReadable(session, userId)) throw ActionPlanNotFoundException()

        return ActionPlanDetailResponse.of(plan, actionPlanMarkdownRenderer.render(plan))
    }
}
