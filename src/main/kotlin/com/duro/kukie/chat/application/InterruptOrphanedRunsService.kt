package com.duro.kukie.chat.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.closeOpenPlans
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.presentation.dto.request.InterruptRunsRequest
import com.duro.kukie.chat.presentation.dto.response.InterruptOrphanedRunsResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InterruptOrphanedRunsService(
    private val chatRunRepository: ChatRunRepository,
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(request: InterruptRunsRequest): InterruptOrphanedRunsResponse {
        val runs = chatRunRepository.lockAllByStatus(RunStatus.RUNNING)
        runs.forEach { run ->
            run.interrupt(request.message)
            actionPlanRepository.closeOpenPlans(run.id)
        }

        return InterruptOrphanedRunsResponse(interrupted = runs.size)
    }
}
