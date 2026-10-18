package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class GetEksCandidatesCommand(
    val userId: UUID,
    val teamId: UUID,
    val accountId: String,
)
