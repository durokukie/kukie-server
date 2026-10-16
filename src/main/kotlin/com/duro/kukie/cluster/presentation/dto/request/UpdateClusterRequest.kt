package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.UpdateClusterCommand
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateClusterRequest(
    @field:Size(max = 100)
    @field:Pattern(regexp = "(?s).*\\S.*", message = "클러스터 이름을 비울 수 없습니다.")
    val name: String? = null,

    @field:Size(max = 63)
    val namespace: String? = null,

    val kubeconfig: String? = null,

    val context: String? = null,
) {
    fun toCommand(clusterId: UUID, userId: UUID) = UpdateClusterCommand(
        clusterId = clusterId,
        userId = userId,
        name = name?.trim(),
        namespace = namespace?.trim()?.ifEmpty { null },
        kubeconfig = kubeconfig,
        context = context?.trim()?.ifEmpty { null },
    )
}
