package com.duro.kukie.team.presentation.dto.response

import com.duro.kukie.team.domain.TeamMembership
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.user.domain.User
import java.time.LocalDateTime
import java.util.UUID

/** 팀 멤버 한 명. 회원 정보(이름·이메일)와 팀에서의 역할을 합친다. */
data class TeamMemberResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val role: TeamRole,
    val joinedAt: LocalDateTime,
) {
    companion object {
        fun of(user: User, membership: TeamMembership) = TeamMemberResponse(
            id = user.id,
            name = user.name,
            email = user.email,
            role = membership.role,
            joinedAt = membership.createdAt,
        )
    }
}
