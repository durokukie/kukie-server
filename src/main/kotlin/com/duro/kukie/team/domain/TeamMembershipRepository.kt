package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.NotTeamMemberException
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TeamMembershipRepository : JpaRepository<TeamMembership, UUID> {
    fun findByTeamIdAndUserId(teamId: UUID, userId: UUID): TeamMembership?

    fun findAllByUserId(userId: UUID): List<TeamMembership>

    fun findAllByTeamId(teamId: UUID): List<TeamMembership>

    fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean

    fun countByTeamIdAndRole(teamId: UUID, role: TeamRole): Long

    fun deleteAllByTeamId(teamId: UUID)
}

/** 그 팀의 멤버가 아니면 예외. 멤버 여부 확인과 역할 조회를 겸한다. */
fun TeamMembershipRepository.findMembershipOrThrow(teamId: UUID, userId: UUID): TeamMembership =
    findByTeamIdAndUserId(teamId, userId) ?: throw NotTeamMemberException()
