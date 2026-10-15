package com.duro.kukie.actionplan.application

import com.duro.kukie.actionplan.application.port.`in`.CreatePlanCommand
import com.duro.kukie.actionplan.domain.ActionPlan
import com.duro.kukie.actionplan.domain.ActionPlanCode
import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.lockByIdOrThrow
import com.duro.kukie.global.util.utcNow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePlanService(
    private val actionPlanRepository: ActionPlanRepository,
    private val chatRunRepository: ChatRunRepository,
) {

    @Transactional
    operator fun invoke(command: CreatePlanCommand): CreatePlanResult {
        chatRunRepository.lockByIdOrThrow(command.runId)
        actionPlanRepository.findByRunIdAndToolCallId(command.runId, command.toolCallId)?.let {
            return CreatePlanResult(PlanResponse.from(it), created = false)
        }

        val plan = actionPlanRepository.saveAndFlush(
            ActionPlan(
                code = nextCode(command.toolName),
                runId = command.runId,
                toolCallId = command.toolCallId,
                toolName = command.toolName,
                risk = command.risk,
                payload = command.payload,
                requestHash = command.requestHash,
            ),
        )

        return CreatePlanResult(PlanResponse.from(plan), created = true)
    }

    private fun nextCode(toolName: String): String {
        val now = utcNow()
        actionPlanRepository.lockCodeGeneration(ActionPlanCode.minutePrefix(now))
        val base = ActionPlanCode.base(now, toolName)

        return ActionPlanCode.next(base, actionPlanRepository.findCodesLike("$base%"))
    }
}
