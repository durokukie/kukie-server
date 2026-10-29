package com.duro.kukie.chat.presentation.dto.response

import com.duro.kukie.actionplan.presentation.dto.response.PlanResponse
import com.duro.kukie.chat.application.ChatAccess
import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatVisibility
import java.util.UUID

data class ChatContextResponse(
    val userId: UUID,
    val conversation: Chat,
    val activeRun: RunResponse?,
    val activeRunPlans: List<PlanResponse>,
    val history: List<History>,
) {
    data class Chat(
        val id: UUID,
        val clusterId: UUID,
        val teamId: UUID,
        val userId: UUID,
        val title: String,
        val shared: Boolean,
        val context: String,
        val namespace: String,
        val clusterFingerprint: String,
        val repoLinkState: Map<String, Any?>?,
    ) {
        companion object {
            fun from(access: ChatAccess): Chat {
                val session = access.session
                return Chat(
                    id = session.id,
                    clusterId = session.clusterId,
                    teamId = access.teamId,
                    userId = session.userId,
                    title = session.title,
                    shared = session.visibility == ChatVisibility.SHARED,
                    context = session.contextName,
                    namespace = session.namespace,
                    clusterFingerprint = session.clusterFingerprint,
                    repoLinkState = session.repoLinkState,
                )
            }
        }
    }

    data class History(
        val runId: UUID,
        val agentMessages: List<Any?>,
    ) {
        companion object {
            fun from(run: ChatRun) = History(runId = run.id, agentMessages = run.agentMessages.orEmpty())
        }
    }
}
