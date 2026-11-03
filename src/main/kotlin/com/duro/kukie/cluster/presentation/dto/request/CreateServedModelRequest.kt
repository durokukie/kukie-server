package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.CreateServedModelCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateServedModelRequest(
    // ServedModel CRD 의 spec.source.huggingface 와 같은 규칙이다
    @field:Size(min = 3, max = 200, message = "모델 이름은 3자 이상 200자 이하여야 합니다.")
    @field:Pattern(
        regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$",
        message = "모델은 허깅페이스의 조직/이름 형식이어야 합니다.",
    )
    val model: String,

    @field:Min(value = 20, message = "저장 공간은 20Gi 이상이어야 합니다.")
    @field:Max(value = 500, message = "저장 공간은 500Gi 이하여야 합니다.")
    val storageGi: Int,
) {
    fun toCommand(clusterId: UUID, userId: UUID) = CreateServedModelCommand(
        clusterId = clusterId,
        userId = userId,
        model = model,
        storageGi = storageGi,
    )
}
