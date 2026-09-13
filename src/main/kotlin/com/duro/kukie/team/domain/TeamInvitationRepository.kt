package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.InvitationNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDateTime
import java.util.UUID

interface TeamInvitationRepository : JpaRepository<TeamInvitation, UUID> {
    /** 같은 팀·주소의 대기 중인 초대는 `uk_team_invitation_pending` 이 하나로 보장한다. */
    fun findByTeamIdAndEmailAndStatus(teamId: UUID, email: String, status: InvitationStatus): TeamInvitation?

    fun findAllByEmailAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
        email: String,
        status: InvitationStatus,
        now: LocalDateTime,
    ): List<TeamInvitation>

    fun deleteAllByTeamId(teamId: UUID)
}

fun TeamInvitationRepository.findByIdOrThrow(id: UUID): TeamInvitation =
    findByIdOrNull(id) ?: throw InvitationNotFoundException()
