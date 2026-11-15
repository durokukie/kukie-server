package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class ModelChatCommand(
    val clusterId: UUID,
    val userId: UUID,
    val name: String,
    val messages: List<ModelChatMessage>,
    val maxTokens: Int,
    val temperature: Double?,
)

/** data class 로 만들지 않는다. toString 에 대화 내용이 실리면 로그로 샐 수 있다. */
class ModelChatMessage(
    val role: String,
    val content: String,
)
