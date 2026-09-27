package com.duro.kukie.auth.application.port.`in`

import java.util.UUID

data class IssueHandoffCodeCommand(
    val userId: UUID,
    val codeChallenge: String,
)
