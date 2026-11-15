package com.duro.kukie.serving.application.port.`in`

import java.util.UUID

data class IssueConnectorCredentialCommand(val clusterId: UUID, val userId: UUID, val models: List<String>)
