package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.InvitationNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

interface TeamInvitationRepository : JpaRepository<TeamInvitation, UUID> {
    fun existsByTeamIdAndEmailAndStatus(teamId: UUID, email: String, status: InvitationStatus): Boolean

    fun findAllByEmailAndStatusOrderByCreatedAtDesc(email: String, status: InvitationStatus): List<TeamInvitation>

    fun deleteAllByTeamId(teamId: UUID)
}

fun TeamInvitationRepository.findByIdOrThrow(id: UUID): TeamInvitation =
    findByIdOrNull(id) ?: throw InvitationNotFoundException()
