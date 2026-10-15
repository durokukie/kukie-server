package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.application.port.`in`.DecidePlanCommand
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.lockByIdOrThrow
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DecidePlanService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(command: DecidePlanCommand): PlanResponse {
        val plan = actionPlanRepository.lockByIdOrThrow(command.planId)
        if (command.approved) {
            plan.approve(checkNotNull(command.call) { "승인에는 호출이 있어야 한다" }, command.userId)
        } else {
            plan.reject(command.userId)
        }
        actionPlanRepository.flush()

        return PlanResponse.from(plan)
    }
}
