package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.application.port.`in`.RecordPlanResultCommand
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.lockByIdOrThrow
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecordPlanResultService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(command: RecordPlanResultCommand): PlanResponse {
        val plan = actionPlanRepository.lockByIdOrThrow(command.planId)
        plan.recordResult(command.success, command.stdout, command.stderr, command.exitCode)
        actionPlanRepository.flush()

        return PlanResponse.from(plan)
    }
}
