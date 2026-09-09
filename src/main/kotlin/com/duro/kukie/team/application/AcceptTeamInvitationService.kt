package com.duro.kukie.team.application

import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.TeamMembership
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.exception.AlreadyTeamMemberException
import com.duro.kukie.team.exception.NotMyInvitationException
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AcceptTeamInvitationService(
    private val teamInvitationRepository: TeamInvitationRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val userRepository: UserRepository,
) {

    /**
     * 초대를 수락하면 그때 멤버십이 생긴다 (제품기획서 02 §4). 새 멤버의 역할은 MEMBER 다.
     *
     * 초대는 이메일 주소로 저장되므로 "내 주소로 온 초대인가"로 본인 확인을 한다.
     */
    @Transactional
    operator fun invoke(invitationId: UUID, userId: UUID) {
        val user = userRepository.findByIdOrThrow(userId)
        val invitation = teamInvitationRepository.findByIdOrThrow(invitationId)
        if (invitation.email != user.email) {
            throw NotMyInvitationException()
        }
        if (teamMembershipRepository.existsByTeamIdAndUserId(invitation.teamId, userId)) {
            throw AlreadyTeamMemberException()
        }

        invitation.accept()
        teamMembershipRepository.save(
            TeamMembership(teamId = invitation.teamId, userId = userId, role = TeamRole.MEMBER),
        )
    }
}
