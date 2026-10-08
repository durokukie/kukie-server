package com.duro.kukie.team.application.port.`in`

import com.duro.kukie.global.domain.Email
import java.util.UUID

data class InviteTeamMemberCommand(
    val teamId: UUID,
    val userId: UUID,
    val email: Email,
)
