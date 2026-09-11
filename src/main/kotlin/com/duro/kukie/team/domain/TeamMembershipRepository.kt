package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.NotTeamMemberException
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface TeamMembershipRepository : JpaRepository<TeamMembership, UUID> {
    fun findByTeamIdAndUserId(teamId: UUID, userId: UUID): TeamMembership?

    fun findAllByUserId(userId: UUID): List<TeamMembership>

    fun findAllByTeamId(teamId: UUID): List<TeamMembership>

    fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean

    /**
     * 그 역할인 멤버십 행을 **잠그고** 읽는다. 세는 것만으로는 동시 요청을 막지 못한다 — 관리자 A·B 가
     * 서로를 동시에 제거하면 두 트랜잭션이 모두 2를 읽고 통과해 관리자 0명인 팀이 남는다.
     *
     * `order by m.id` 는 두 트랜잭션이 같은 순서로 잠그게 해 교착을 피하려는 것이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from TeamMembership m where m.teamId = :teamId and m.role = :role order by m.id")
    fun lockAllByTeamIdAndRole(@Param("teamId") teamId: UUID, @Param("role") role: TeamRole): List<TeamMembership>

    fun deleteAllByTeamId(teamId: UUID)
}

fun TeamMembershipRepository.findByTeamIdAndUserIdOrThrow(teamId: UUID, userId: UUID): TeamMembership =
    findByTeamIdAndUserId(teamId, userId) ?: throw NotTeamMemberException()
