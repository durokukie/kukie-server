package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.application.port.`in`.RecordSyncResultCommand
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.lockByIdOrThrow
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecordSyncResultService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(command: RecordSyncResultCommand): PlanResponse {
        val plan = actionPlanRepository.lockByIdOrThrow(command.planId)
        plan.recordSyncResult(command.syncResult)
        actionPlanRepository.flush()

        return PlanResponse.from(plan)
    }
}
