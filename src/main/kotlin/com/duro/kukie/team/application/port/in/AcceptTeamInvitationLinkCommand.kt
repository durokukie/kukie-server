package com.duro.kukie.team.application.port.`in`

import java.util.UUID

data class AcceptTeamInvitationLinkCommand(
    val userId: UUID,
    val token: String,
)
