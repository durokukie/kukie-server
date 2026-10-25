package com.duro.kukie.cluster.application.port.`in`

import java.util.UUID

data class GetEksCandidatesCommand(
    val userId: UUID,
    val connectionId: UUID,
    val accountId: String,
)
