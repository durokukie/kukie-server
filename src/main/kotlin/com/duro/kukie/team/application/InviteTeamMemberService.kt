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
import com.duro.kukie.global.util.logger
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

    private val log = logger()

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
            notifyByMail(email, team.name, userId)
        }

        return TeamInvitationResponse.of(invitation)
    }

    /**
     * 메일은 최선 노력이다.
     *
     * 이 서비스는 @Transactional 이라 여기서 예외가 나가면 **방금 만든 초대까지 롤백된다**. SMTP 가
     * 잠깐 죽었다고 관리자가 초대를 아예 못 하게 되는 편이 더 나쁘다. 초대는 남으므로 상대가 가입하면
     * 받은 초대함에서 보게 된다 (제품기획서 02 §4).
     */
    private fun notifyByMail(email: String, teamName: String, inviterId: UUID) {
        try {
            teamInvitationSender.send(email, teamName, userRepository.findByIdOrThrow(inviterId).name)
        } catch (exception: Exception) {
            log.error("초대 메일을 보내지 못했다 — 초대는 그대로 남는다: {} ({} 팀)", email, teamName, exception)
        }
    }
}
