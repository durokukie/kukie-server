package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.application.port.`in`.FailPlanCommand
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.lockByIdOrThrow
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FailPlanService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(command: FailPlanCommand): PlanResponse {
        val plan = actionPlanRepository.lockByIdOrThrow(command.planId)
        plan.fail(command.reason)
        actionPlanRepository.flush()

        return PlanResponse.from(plan)
    }
}
