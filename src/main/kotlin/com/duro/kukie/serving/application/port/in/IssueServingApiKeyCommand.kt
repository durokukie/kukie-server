package com.duro.kukie.serving.application.port.`in`

import java.util.UUID

data class IssueServingApiKeyCommand(val accessId: UUID, val userId: UUID, val name: String, val expiresInDays: Int)
