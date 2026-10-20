package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.presentation.dto.response.SentTeamInvitationResponse
import com.duro.kukie.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetTeamInvitationsService(
    private val teamInvitationRepository: TeamInvitationRepository,
    private val userRepository: UserRepository,
) {

    /** 팀이 보낸 초대 전부, 최신순. 관리자만 볼 수 있다. */
    @Transactional(readOnly = true)
    operator fun invoke(teamId: UUID): List<SentTeamInvitationResponse> {
        val invitations = teamInvitationRepository.findAllByTeamIdOrderByCreatedAtDesc(teamId)
        val inviters = userRepository.findAllById(invitations.map { it.invitedBy }.distinct()).associateBy { it.id }

        return invitations.map { SentTeamInvitationResponse.of(it, inviters[it.invitedBy]) }
    }
}
