package com.duro.kukie.team.application

import com.duro.kukie.global.util.normalizeEmail
import com.duro.kukie.team.application.port.`in`.InviteTeamMemberCommand
import com.duro.kukie.team.domain.InvitationStatus
import com.duro.kukie.team.domain.TeamInvitation
import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.exception.AlreadyTeamMemberException
import com.duro.kukie.team.exception.InvitationAlreadySentException
import com.duro.kukie.team.presentation.dto.response.TeamInvitationResponse
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class InviteTeamMemberService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val teamInvitationRepository: TeamInvitationRepository,
    private val userRepository: UserRepository,
    private val events: ApplicationEventPublisher,
) {

    /** 초대는 Admin만. 기한이 지난 대기 초대는 EXPIRED 로 정리하고 새로 보낸다. */
    @Transactional
    operator fun invoke(command: InviteTeamMemberCommand): TeamInvitationResponse {
        val team = teamRepository.findByIdOrThrow(command.teamId)
        val email = command.email.normalizeEmail()

        // email 유니크가 대소문자를 구분해 같은 주소의 변형이 여럿일 수 있다
        val invitees = userRepository.findAllByEmailIgnoreCase(email)
        if (invitees.any { teamMembershipRepository.existsByTeamIdAndUserId(command.teamId, it.id) }) {
            throw AlreadyTeamMemberException()
        }
        expirePendingInvitation(command.teamId, email)

        val invitation = teamInvitationRepository.save(
            TeamInvitation(teamId = command.teamId, email = email, invitedBy = command.userId),
        )
        if (invitees.isEmpty()) {
            // 메일은 커밋 뒤에 나간다 (TeamInvitationMailListener)
            events.publishEvent(TeamMemberInvited(email, team.name, userRepository.findByIdOrThrow(command.userId).name))
        }

        return TeamInvitationResponse.of(invitation)
    }

    /** 아직 유효한 대기 초대가 있으면 409, 기한이 지났으면 EXPIRED 로 바꿔 자리를 비운다. */
    private fun expirePendingInvitation(teamId: UUID, email: String) {
        val pending = teamInvitationRepository.findByTeamIdAndEmailAndStatus(teamId, email, InvitationStatus.PENDING)
            ?: return
        if (pending.isExpired.not()) {
            throw InvitationAlreadySentException()
        }

        pending.expire()
        // Hibernate 는 INSERT 를 UPDATE 보다 먼저 내보낸다. 상태 변경을 먼저 반영하지 않으면 새 초대의
        // INSERT 가 `uk_team_invitation_pending` 에 걸린다.
        teamInvitationRepository.flush()
    }
}
