package com.duro.kukie.cluster.presentation.dto.response

/** data class 로 만들지 않는다. toString 에 대화 내용이 실리면 디버그 로그로 샐 수 있다. */
class ModelChatDelta(
    val content: String?,
    val reasoning: String?,
)

data class ModelChatDone(
    val usage: ModelChatUsage?,
    val ttftMs: Long?,
    val tokensPerSecond: Double?,
    val finishReason: String?,
)

data class ModelChatUsage(
    val promptTokens: Long?,
    val completionTokens: Long?,
    val totalTokens: Long?,
)
