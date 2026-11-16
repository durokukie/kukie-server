package com.duro.kukie.team.application

import com.duro.kukie.team.application.port.`in`.AcceptTeamInvitationLinkCommand
import com.duro.kukie.team.application.port.out.TeamInvitationLinkCodec
import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import com.duro.kukie.team.exception.InvalidInvitationLinkException
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AcceptTeamInvitationLinkService(
    private val teamInvitationLinkCodec: TeamInvitationLinkCodec,
    private val teamInvitationRepository: TeamInvitationRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamRepository: TeamRepository,
    private val acceptTeamInvitationService: AcceptTeamInvitationService,
) {

    /** 이미 그 팀 구성원이면 그대로 팀을 돌려준다 — 같은 링크를 다시 눌러도 오류가 아니다. */
    @Transactional
    operator fun invoke(command: AcceptTeamInvitationLinkCommand): TeamResponse {
        val invitationId = teamInvitationLinkCodec.decode(command.token) ?: throw InvalidInvitationLinkException()
        val invitation = teamInvitationRepository.findByIdOrThrow(invitationId)
        if (!teamMembershipRepository.existsByTeamIdAndUserId(invitation.teamId, command.userId)) {
            acceptTeamInvitationService(invitationId, command.userId)
        }

        val team = teamRepository.findByIdOrThrow(invitation.teamId)
        val membership = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(invitation.teamId, command.userId)
        return TeamResponse.of(team, membership.role)
    }
}
