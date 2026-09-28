package com.duro.kukie.team.application

/** 초대 저장 이벤트. 커밋 뒤에 쓰이므로 엔티티 대신 값만 담는다. */
data class TeamMemberInvited(
    val email: String,
    val teamName: String,
    val inviterName: String,
)
