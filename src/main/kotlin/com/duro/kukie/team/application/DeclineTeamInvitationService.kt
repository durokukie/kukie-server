package com.duro.kukie.team.application

import com.duro.kukie.global.util.normalizeEmail
import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.exception.NotMyInvitationException
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeclineTeamInvitationService(
    private val teamInvitationRepository: TeamInvitationRepository,
    private val userRepository: UserRepository,
) {

    /** 거절해도 기록은 남긴다. 같은 주소를 다시 초대할 수 있다. */
    @Transactional
    operator fun invoke(invitationId: UUID, userId: UUID) {
        val user = userRepository.findByIdOrThrow(userId)
        val invitation = teamInvitationRepository.findByIdOrThrow(invitationId)
        if (invitation.email != user.email.normalizeEmail()) {
            throw NotMyInvitationException()
        }

        invitation.decline()
    }
}
