package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.application.port.`in`.OfferPlanCommand
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.lockByIdOrThrow
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OfferPlanService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(command: OfferPlanCommand): PlanResponse {
        val plan = actionPlanRepository.lockByIdOrThrow(command.planId)
        plan.offer(command.decisionGuidance)
        actionPlanRepository.flush()

        return PlanResponse.from(plan)
    }
}
