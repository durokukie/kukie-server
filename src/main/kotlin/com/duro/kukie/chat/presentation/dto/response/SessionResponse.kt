package com.duro.kukie.chat.presentation.dto.response

import com.duro.kukie.chat.domain.ChatSession

data class SessionResponse(
    val context: String,
    val namespace: String,
    val pending: List<String>,
) {
    companion object {
        fun of(session: ChatSession, pending: List<String>) = SessionResponse(
            context = session.contextName,
            namespace = session.namespace,
            pending = pending,
        )
    }
}
