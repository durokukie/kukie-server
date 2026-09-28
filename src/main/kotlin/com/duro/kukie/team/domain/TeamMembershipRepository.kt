package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.NotTeamMemberException
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TeamMembershipRepository : JpaRepository<TeamMembership, UUID> {
    fun findByTeamIdAndUserId(teamId: UUID, userId: UUID): TeamMembership?

    fun findAllByUserId(userId: UUID): List<TeamMembership>

    fun findAllByTeamId(teamId: UUID): List<TeamMembership>

    fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean

    /** 그 역할의 멤버십을 잠그고 읽는다. `order by m.id` 로 잠금 순서를 맞춰 교착을 피한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from TeamMembership m where m.teamId = :teamId and m.role = :role order by m.id")
    fun lockAllByTeamIdAndRole(@Param("teamId") teamId: UUID, @Param("role") role: TeamRole): List<TeamMembership>

    fun deleteAllByTeamId(teamId: UUID)
}

fun TeamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId: UUID, userId: UUID): TeamMembership =
    findByTeamIdAndUserId(teamId, userId) ?: throw NotTeamMemberException()
