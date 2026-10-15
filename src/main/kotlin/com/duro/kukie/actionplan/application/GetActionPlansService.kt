package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.actionplan.presentation.dto.response.ActionPlanSummaryResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetActionPlansService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID, clusterId: UUID?, status: PlanStatus?): List<ActionPlanSummaryResponse> {
        val rows = actionPlanRepository.findAllVisibleTo(userId, clusterId, status)
        if (rows.isEmpty()) return emptyList()

        // status 필터로 빠진 같은 run 의 카드도 세야 하므로 따로 센다
        val approvals = actionPlanRepository.countByRunIds(rows.map { it.plan.runId }.toSet())
            .associate { it.runId to it.plans }

        return rows.map { ActionPlanSummaryResponse.of(it, approvals.getValue(it.plan.runId)) }
    }
}
