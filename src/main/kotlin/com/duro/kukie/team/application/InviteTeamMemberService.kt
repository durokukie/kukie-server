package com.duro.kukie.team.application

import com.duro.kukie.global.util.normalizeEmail
import com.duro.kukie.team.domain.InvitationStatus
import com.duro.kukie.team.domain.TeamInvitation
import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.exception.AlreadyTeamMemberException
import com.duro.kukie.team.exception.InvitationAlreadySentException
import com.duro.kukie.team.presentation.dto.request.InviteTeamMemberRequest
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
    private val teamPermission: TeamPermission,
    private val events: ApplicationEventPublisher,
) {

    /**
     * 초대는 Admin만 보낸다. 보냈다고 바로 멤버가 되지는 않는다 — 받는 사람이 수락해야 한다
     * (제품기획서 02 §4).
     *
     * 아직 가입하지 않은 주소도 초대할 수 있다. 그 경우 가입을 안내하는 메일을 보내고, 가입한 뒤
     * 자기 Inbox 에서 같은 초대를 보게 된다.
     *
     * 주소는 소문자로 맞춰 저장한다 — 대소문자만 다른 주소로 초대하면 그 초대는 상대의 초대함에
     * 뜨지 않고 수락도 재초대도 막혀 영영 남는다 (자동 리뷰 지적).
     *
     * 초대에는 유효 기간이 있다([TeamInvitation.VALIDITY]). 기한이 지난 대기 초대가 있으면
     * 그것을 EXPIRED 로 정리하고 새로 보낸다.
     */
    @Transactional
    operator fun invoke(teamId: UUID, userId: UUID, request: InviteTeamMemberRequest): TeamInvitationResponse {
        teamPermission.requireAdmin(teamId, userId)

        val team = teamRepository.findByIdOrThrow(teamId)
        val email = request.email.normalizeEmail()

        // tbl_user.email 유니크는 대소문자를 구분해서 같은 주소의 변형이 여럿일 수 있다. 하나라도
        // 이미 멤버면 초대할 이유가 없다.
        val invitees = userRepository.findAllByEmailIgnoreCase(email)
        if (invitees.any { teamMembershipRepository.existsByTeamIdAndUserId(teamId, it.id) }) {
            throw AlreadyTeamMemberException()
        }
        expirePendingInvitation(teamId, email)

        val invitation = teamInvitationRepository.save(TeamInvitation(teamId = teamId, email = email, invitedBy = userId))
        if (invitees.isEmpty()) {
            // 발송은 커밋 뒤에. 트랜잭션 안에서 보내면 SMTP 를 기다리는 동안 DB 커넥션이 묶이고,
            // 보낸 뒤 커밋이 실패하면 없는 초대의 메일이 나간다 (TeamInvitationMailListener).
            events.publishEvent(TeamMemberInvited(email, team.name, userRepository.findByIdOrThrow(userId).name))
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
