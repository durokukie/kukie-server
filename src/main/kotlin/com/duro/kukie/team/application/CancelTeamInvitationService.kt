package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.exception.InvitationNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CancelTeamInvitationService(
    private val teamInvitationRepository: TeamInvitationRepository,
) {

    /** 초대 취소는 Admin만. 다른 팀의 초대는 존재를 숨기려고 404 로 응답한다. */
    @Transactional
    operator fun invoke(teamId: UUID, invitationId: UUID) {
        val invitation = teamInvitationRepository.findByIdOrThrow(invitationId)
        if (invitation.teamId != teamId) {
            throw InvitationNotFoundException()
        }

        invitation.cancel()
    }
}
