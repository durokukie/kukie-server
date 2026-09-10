package com.duro.kukie.team.application

/**
 * 초대가 저장됐다는 사건. 안내 메일은 이 사건을 받아 **커밋 뒤에** 보낸다.
 *
 * 엔티티가 아니라 값만 담는다 — 커밋 뒤에는 영속성 컨텍스트가 닫혀 있어 지연 로딩을 할 수 없다.
 */
data class TeamMemberInvited(
    val email: String,
    val teamName: String,
    val inviterName: String,
)
