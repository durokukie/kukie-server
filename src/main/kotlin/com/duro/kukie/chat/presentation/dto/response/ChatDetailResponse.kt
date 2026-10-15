package com.duro.kukie.chat.presentation.dto.response

data class ChatDetailResponse(
    val conversation: ChatResponse,
    val session: SessionResponse,
    val turns: List<TurnResponse>,
)
