package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.StartAwsConnectionCommand
import java.util.UUID

data class StartAwsConnectionRequest(
    val teamId: UUID,
) {
    fun toCommand(userId: UUID) = StartAwsConnectionCommand(userId = userId, teamId = teamId)
}
