package com.duro.kukie.chat.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.actionplan.domain.closeOpenPlans
import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.chat.application.port.`in`.UpdateRunCommand
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.FirstMessageTitle
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.findSessionIdOrThrow
import com.duro.kukie.chat.domain.flushOrThrowActiveRunExists
import com.duro.kukie.chat.domain.lockByIdOrThrow
import com.duro.kukie.chat.presentation.dto.response.RunResponse
import com.duro.kukie.chat.presentation.dto.response.UpdateRunResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateRunService(
    private val chatRunRepository: ChatRunRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val actionPlanRepository: ActionPlanRepository,
) {

    @Transactional
    operator fun invoke(command: UpdateRunCommand): UpdateRunResponse {
        // 교착을 막으려고 run 시작과 같은 방 → run 순서로 잠근다
        val session = if (command.currentMode != null || command.titleFrom != null) {
            chatSessionRepository.lockByIdOrThrow(chatRunRepository.findSessionIdOrThrow(command.runId))
        } else {
            null
        }
        val run = chatRunRepository.lockByIdOrThrow(command.runId)
        command.responsePayload?.let(run::recordResponse)
        command.appendAgentMessages?.let(run::appendAgentMessages)
        command.usageSummary?.let(run::recordUsage)
        val closedPlans = command.status?.let { status ->
            run.changeStatus(status)
            chatRunRepository.flushOrThrowActiveRunExists()
            if (status.active) emptyList() else actionPlanRepository.closeOpenPlans(run.id)
        }.orEmpty()

        session?.let {
            command.currentMode?.let(it::changeMode)
            command.titleFrom?.let { text -> nameFromFirstMessage(it, run, text) }
        }
        chatRunRepository.flush()

        return UpdateRunResponse(RunResponse.from(run), closedPlans.map(PlanResponse::from))
    }

    private fun nameFromFirstMessage(session: ChatSession, run: ChatRun, text: String) {
        if (!session.hasDefaultTitle || run.kind != RunKind.CHAT || !FirstMessageTitle.titleable(text)) return
        val others = chatRunRepository.findInputTexts(session.id, RunKind.CHAT, exceptRunId = run.id)
        if (others.any(FirstMessageTitle::titleable)) return

        session.rename(FirstMessageTitle.of(text))
    }
}
