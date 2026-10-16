package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.lockByIdOrThrow
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MarkPlanUnknownService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(planId: UUID): PlanResponse {
        val plan = actionPlanRepository.lockByIdOrThrow(planId)
        plan.markUnknown()
        actionPlanRepository.flush()

        return PlanResponse.from(plan)
    }
}
