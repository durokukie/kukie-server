package com.duro.kukie.serving.presentation.dto.request

import com.duro.kukie.serving.application.port.`in`.ConfigureServingEndpointCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.UUID

class ConfigureServingEndpointRequest(
    @field:NotBlank @field:Size(max = 512) val baseUrl: String,
    @field:NotBlank @field:Size(max = 4096) val serviceToken: String,
    @field:NotEmpty @field:Size(max = 100) val models: List<String>,
) {
    fun toCommand(clusterId: UUID, userId: UUID) = ConfigureServingEndpointCommand(clusterId, userId, baseUrl, serviceToken, models)
    override fun toString() = "ConfigureServingEndpointRequest([REDACTED])"
}
