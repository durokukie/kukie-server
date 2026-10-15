package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.RegisterClusterCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterClusterRequest(
    @field:NotBlank
    val kubeconfig: String,

    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    val context: String? = null,

    @field:Size(max = 63)
    val namespace: String? = null,

    val teamId: UUID,
) {
    fun toCommand(userId: UUID) = RegisterClusterCommand(
        userId = userId,
        teamId = teamId,
        name = name.trim(),
        kubeconfig = kubeconfig,
        context = context?.trim()?.ifEmpty { null },
        namespace = namespace?.trim()?.ifEmpty { null },
    )
}
