package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.findByIdOrThrow
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetPlanService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(planId: UUID): PlanResponse = PlanResponse.from(actionPlanRepository.findByIdOrThrow(planId))
}
