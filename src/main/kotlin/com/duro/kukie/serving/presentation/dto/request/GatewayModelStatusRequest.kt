package com.duro.kukie.serving.presentation.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class GatewayModelStatusRequest(@field:NotNull @field:Size(max = 256) @field:Valid val statuses: List<GatewayModelStatusItem?>?) {
    @get:AssertTrue val validItems: Boolean get() = statuses != null && statuses.none { it == null }
}
data class GatewayModelStatusItem(
    @field:NotNull val clusterId: UUID,
    @field:NotNull @field:Pattern(regexp = "[0-9a-f]{64}") val tokenSha256: String,
    val connected: Boolean,
    val checkedAt: Instant?,
    @field:NotNull @field:Size(max = 100) val readyModels: List<String?>?,
) {
    @get:AssertTrue val validModels: Boolean get() = readyModels != null && readyModels.all { it != null && it.length in 1..200 && it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")) }
}
