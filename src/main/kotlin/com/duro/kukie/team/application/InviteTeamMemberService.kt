package com.duro.kukie.team.application

import com.duro.kukie.team.application.port.out.TeamInvitationSender
import com.duro.kukie.team.domain.InvitationStatus
import com.duro.kukie.team.domain.TeamInvitation
import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamPermission
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.exception.AlreadyTeamMemberException
import com.duro.kukie.team.exception.InvitationAlreadySentException
import com.duro.kukie.team.presentation.dto.request.InviteTeamMemberRequest
import com.duro.kukie.team.presentation.dto.response.TeamInvitationResponse
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class InviteTeamMemberService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamInvitationRepository: TeamInvitationRepository,
    private val userRepository: UserRepository,
    private val teamPermission: TeamPermission,
    private val teamInvitationSender: TeamInvitationSender,
) {

    /**
     * 초대는 Admin만 보낸다. 보냈다고 바로 멤버가 되지는 않는다 — 받는 사람이 수락해야 한다
     * (제품기획서 02 §4).
     *
     * 아직 가입하지 않은 주소도 초대할 수 있다. 그 경우 가입을 안내하는 메일을 보내고, 가입한 뒤
     * 자기 Inbox 에서 같은 초대를 보게 된다.
     */
    @Transactional
    operator fun invoke(teamId: UUID, userId: UUID, request: InviteTeamMemberRequest): TeamInvitationResponse {
        teamPermission.requireAdmin(teamId, userId)

        val team = teamRepository.findByIdOrThrow(teamId)
        val email = request.email.trim()

        val invitee = userRepository.findByEmail(email)
        if (invitee != null && teamMembershipRepository.existsByTeamIdAndUserId(teamId, invitee.id)) {
            throw AlreadyTeamMemberException()
        }
        if (teamInvitationRepository.existsByTeamIdAndEmailAndStatus(teamId, email, InvitationStatus.PENDING)) {
            throw InvitationAlreadySentException()
        }

        val invitation = teamInvitationRepository.save(TeamInvitation(teamId = teamId, email = email, invitedBy = userId))
        if (invitee == null) {
            teamInvitationSender.send(email, team.name, userRepository.findByIdOrThrow(userId).name)
        }

        return TeamInvitationResponse.of(invitation)
    }
}
