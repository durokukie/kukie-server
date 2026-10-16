package com.duro.kukie.chat.presentation.dto.response

import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatVisibility
import java.time.LocalDateTime
import java.util.UUID

data class ChatResponse(
    val id: UUID,
    val clusterId: UUID,
    val teamId: UUID,
    val userId: UUID,
    val title: String,
    val shared: Boolean,
    val version: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val running: Boolean,
) {
    companion object {
        fun of(session: ChatSession, teamId: UUID, running: Boolean) = ChatResponse(
            id = session.id,
            clusterId = session.clusterId,
            teamId = teamId,
            userId = session.userId,
            title = session.title,
            shared = session.visibility == ChatVisibility.SHARED,
            version = session.version,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            running = running,
        )
    }
}
