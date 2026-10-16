package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.application.port.`in`.RecordDryRunCommand
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.lockByIdOrThrow
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecordDryRunService(
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(command: RecordDryRunCommand): PlanResponse {
        val plan = actionPlanRepository.lockByIdOrThrow(command.planId)
        plan.recordDryRun(command.status, command.stdout, command.stderr)
        actionPlanRepository.flush()

        return PlanResponse.from(plan)
    }
}
