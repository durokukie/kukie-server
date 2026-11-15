package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.actionplan.presentation.dto.response.UnsettledPlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetUnsettledPlansService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(toolName: String): List<UnsettledPlanResponse> {
        val rows = actionPlanRepository.findUnsettled(toolName, ActionPlan.SYNC_WATCHING)
        val plans = actionPlanRepository.findAllById(rows.map { it.planId }).associateBy { it.id }

        return rows.mapNotNull { row ->
            plans[row.planId]?.let { UnsettledPlanResponse(PlanResponse.from(it), row.clusterId, row.runActive) }
        }
    }
}
