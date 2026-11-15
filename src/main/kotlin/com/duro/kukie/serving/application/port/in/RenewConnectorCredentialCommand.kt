package com.duro.kukie.serving.application.port.`in`

import java.util.UUID

data class RenewConnectorCredentialCommand(val clusterId: UUID, val tokenSha256: String)
