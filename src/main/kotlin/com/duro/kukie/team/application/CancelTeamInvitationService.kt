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
    private val teamPermission: TeamPermission,
) {

    /**
     * 보낸 초대를 거둬들인다. Admin만 할 수 있고, 아직 수락·거절되지 않은 초대만 취소된다.
     *
     * 다른 팀의 초대 id 를 넣으면 403 이 아니라 **404** 다 — 남의 팀에 어떤 초대가 있는지를
     * 응답 코드로 알려 주지 않는다.
     */
    @Transactional
    operator fun invoke(teamId: UUID, invitationId: UUID, userId: UUID) {
        teamPermission.requireAdmin(teamId, userId)

        val invitation = teamInvitationRepository.findByIdOrThrow(invitationId)
        if (invitation.belongsTo(teamId).not()) {
            throw InvitationNotFoundException()
        }

        invitation.cancel()
    }
}
