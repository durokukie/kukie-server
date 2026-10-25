package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.RegisterEksClusterCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterEksClusterRequest(
    val connectionId: UUID,

    @field:Pattern(regexp = "\\d{12}")
    val accountId: String,

    @field:NotBlank
    val region: String,

    @field:Size(max = 100)
    @field:Pattern(regexp = "[0-9A-Za-z][A-Za-z0-9_-]*")
    val clusterName: String,

    @field:NotBlank
    val namespace: String,

    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
) {
    fun toCommand(userId: UUID) = RegisterEksClusterCommand(
        userId = userId,
        connectionId = connectionId,
        accountId = accountId,
        region = region.trim(),
        clusterName = clusterName,
        namespace = namespace.trim(),
        name = name.trim(),
    )
}
