package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.ServedModelName
import com.duro.kukie.cluster.application.port.`in`.CreateServedModelCommand
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import java.util.UUID

data class CreateServedModelRequest(
    @field:Size(min = 3, max = 200, message = "모델 이름은 3자 이상 200자 이하여야 합니다.")
    @field:Pattern(
        regexp = ServedModelName.MODEL_PATTERN,
        message = "모델은 허깅페이스의 조직/이름 형식이어야 합니다.",
    )
    val model: String,

    @field:Min(value = 20, message = "저장 공간은 20Gi 이상이어야 합니다.")
    @field:Max(value = 500, message = "저장 공간은 500Gi 이하여야 합니다.")
    val storageGi: Int,

    @JsonDeserialize(using = WholeNumberDeserializer::class)
    val gpus: Int? = null,
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "GPU 수는 1, 2, 4, 8 중 하나여야 합니다.")
    val gpusAllowed: Boolean
        get() = gpus == null || gpus in ALLOWED_GPUS

    fun toCommand(clusterId: UUID, userId: UUID) = CreateServedModelCommand(
        clusterId = clusterId,
        userId = userId,
        model = model,
        storageGi = storageGi,
        gpus = gpus ?: DEFAULT_GPUS,
    )

    companion object {
        private const val DEFAULT_GPUS = 1
        private val ALLOWED_GPUS = setOf(1, 2, 4, 8)
    }
}

/** Jackson 기본값은 2.5 를 2 로, "2" 를 2 로 바꿔 받아 다른 장수로 시작하게 된다. */
internal class WholeNumberDeserializer : ValueDeserializer<Int>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Int {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return context.handleUnexpectedToken(Int::class.java, parser) as Int
        return parser.intValue
    }
}
