package com.duro.kukie.serving.application.port.`in`

import com.duro.kukie.global.domain.Email
import java.util.UUID

data class InviteServingUserCommand(val clusterId: UUID, val userId: UUID, val email: Email)
