package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.ExtendServedModelCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.util.UUID

data class ExtendServedModelRequest(
    @field:Min(value = 1, message = "연장 시간은 1시간 이상이어야 합니다.")
    @field:Max(value = 4, message = "연장 시간은 4시간 이하여야 합니다.")
    val hours: Int,
) {
    fun toCommand(clusterId: UUID, userId: UUID, name: String) = ExtendServedModelCommand(
        clusterId = clusterId,
        userId = userId,
        name = name,
        hours = hours,
    )
}
