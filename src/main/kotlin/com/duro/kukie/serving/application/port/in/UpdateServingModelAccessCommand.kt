package com.duro.kukie.serving.application.port.`in`

import java.util.UUID

data class UpdateServingModelAccessCommand(val clusterId: UUID, val userId: UUID, val model: String, val enabled: Boolean)
