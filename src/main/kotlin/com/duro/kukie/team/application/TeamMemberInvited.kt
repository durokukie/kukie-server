package com.duro.kukie.team.application

import java.util.UUID

/** 초대 저장 이벤트. 커밋 뒤에 쓰이므로 엔티티 대신 값만 담는다. */
data class TeamMemberInvited(
    val invitationId: UUID,
    val email: String,
    val teamName: String,
    val inviterName: String,
    val registered: Boolean,
)
