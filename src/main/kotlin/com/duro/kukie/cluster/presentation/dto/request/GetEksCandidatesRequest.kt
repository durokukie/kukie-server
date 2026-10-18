package com.duro.kukie.cluster.presentation.dto.request

import com.duro.kukie.cluster.application.port.`in`.GetEksCandidatesCommand
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class GetEksCandidatesRequest(
    val teamId: UUID,

    @field:Pattern(regexp = "\\d{12}")
    val accountId: String,
) {
    fun toCommand(userId: UUID) = GetEksCandidatesCommand(userId = userId, teamId = teamId, accountId = accountId)
}
