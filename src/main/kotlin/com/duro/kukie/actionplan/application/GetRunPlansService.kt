package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.exception.RunNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetRunPlansService(
    private val actionPlanRepository: ActionPlanRepository,
    private val chatRunRepository: ChatRunRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(runId: UUID, toolCallId: String?): List<PlanResponse> {
        if (!chatRunRepository.existsById(runId)) throw RunNotFoundException()
        if (toolCallId != null) {
            return listOfNotNull(actionPlanRepository.findByRunIdAndToolCallId(runId, toolCallId)).map(PlanResponse::from)
        }

        return actionPlanRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(runId).map(PlanResponse::from)
    }
}
