package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.ModelChatCommand
import com.duro.kukie.cluster.application.port.`in`.ModelChatMessage
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class ModelChatRequest(
    @field:NotEmpty(message = "메시지가 하나 이상 있어야 합니다.")
    @field:Size(max = 200, message = "메시지는 200개 이하여야 합니다.")
    @field:Valid
    @param:JsonSetter(contentNulls = Nulls.FAIL)
    val messages: List<ModelChatMessageRequest>,

    @field:Min(value = 1, message = "최대 토큰은 1 이상이어야 합니다.")
    @field:Max(value = 2048, message = "최대 토큰은 2048 이하여야 합니다.")
    val maxTokens: Int? = null,

    @field:DecimalMin(value = "0.0", message = "온도는 0 이상이어야 합니다.")
    @field:DecimalMax(value = "2.0", message = "온도는 2 이하여야 합니다.")
    val temperature: Double? = null,
) {
    fun toCommand(clusterId: UUID, userId: UUID, name: String) = ModelChatCommand(
        clusterId = clusterId,
        userId = userId,
        name = name,
        messages = messages.map { ModelChatMessage(role = it.role, content = it.content) },
        maxTokens = maxTokens ?: DEFAULT_MAX_TOKENS,
        temperature = temperature,
    )

    private companion object {
        const val DEFAULT_MAX_TOKENS = 512
    }
}

/** data class 로 만들지 않는다. toString 에 대화 내용이 실리면 로그로 샐 수 있다. */
class ModelChatMessageRequest(
    @field:Pattern(regexp = "^(system|user|assistant)$", message = "role 은 system, user, assistant 중 하나여야 합니다.")
    val role: String,

    val content: String,
)
