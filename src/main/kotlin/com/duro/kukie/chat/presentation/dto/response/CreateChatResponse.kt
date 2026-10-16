package com.duro.kukie.chat.presentation.dto.response

data class CreateChatResponse(
    val conversation: ChatResponse,
    val session: SessionResponse,
)
