package com.duro.kukie.serving.presentation.dto.request

import com.duro.kukie.serving.application.port.`in`.RenewConnectorCredentialCommand
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class GatewayRenewConnectorCredentialRequest(
    @field:NotNull val clusterId: UUID?,
    @field:NotNull @field:Pattern(regexp = "[0-9a-f]{64}") val tokenSha256: String?,
) {
    fun toCommand() = RenewConnectorCredentialCommand(clusterId!!, tokenSha256!!)
}
