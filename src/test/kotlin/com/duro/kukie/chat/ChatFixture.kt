package com.duro.kukie.chat

import com.duro.kukie.chat.domain.ChatRun
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.RunStatus
import java.util.UUID

object ChatFixture {

    const val DEFAULT_CONTEXT = "prod"
    const val DEFAULT_NAMESPACE = "web"
    val FINGERPRINT = "a".repeat(64)

    fun session(
        userId: UUID,
        clusterId: UUID,
        visibility: ChatVisibility = ChatVisibility.PRIVATE,
        title: String = ChatSession.DEFAULT_TITLE,
    ): ChatSession = ChatSession(
        userId = userId,
        clusterId = clusterId,
        title = title,
        contextName = DEFAULT_CONTEXT,
        namespace = DEFAULT_NAMESPACE,
        clusterFingerprint = FINGERPRINT,
        visibility = visibility,
    )

    fun run(
        sessionId: UUID,
        turnNo: Int = 1,
        requestId: String = "req-$turnNo",
        kind: RunKind = RunKind.CHAT,
        inputText: String? = "파드 목록 보여줘",
        status: RunStatus = RunStatus.RUNNING,
    ): ChatRun = ChatRun(
        sessionId = sessionId,
        requestId = requestId,
        turnNo = turnNo,
        kind = kind,
        mode = "학습",
        inputText = inputText,
    ).apply { if (status != RunStatus.RUNNING) changeStatus(status) }
}
