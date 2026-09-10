package com.duro.kukie.inbox.application

import com.duro.kukie.inbox.presentation.dto.response.InboxResponse
import com.duro.kukie.inbox.presentation.dto.response.InvitationResponse
import com.duro.kukie.inbox.presentation.dto.response.NotificationResponse
import com.duro.kukie.global.util.normalizeEmail
import com.duro.kukie.notification.domain.NotificationRepository
import com.duro.kukie.team.domain.InvitationStatus
import com.duro.kukie.team.domain.TeamInvitationRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetInboxService(
    private val teamInvitationRepository: TeamInvitationRepository,
    private val notificationRepository: NotificationRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
) {

    /**
     * 아직 처리하지 않은 초대와 알림. 초대는 이메일 주소로 저장되므로 내 주소로 온 것을 찾는다 —
     * 초대받은 뒤에 가입한 사람도 그래서 자기 초대를 보게 된다 (제품기획서 02 §4).
     */
    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): InboxResponse {
        val user = userRepository.findByIdOrThrow(userId)

        val invitations = teamInvitationRepository
            .findAllByEmailAndStatusOrderByCreatedAtDesc(user.email.normalizeEmail(), InvitationStatus.PENDING)
        val teams = teamRepository.findAllById(invitations.map { it.teamId }).associateBy { it.id }
        val inviters = userRepository.findAllById(invitations.map { it.invitedBy }).associateBy { it.id }

        return InboxResponse(
            invitations = invitations.mapNotNull { invitation ->
                teams[invitation.teamId]?.let { team ->
                    InvitationResponse.of(invitation, team, inviters[invitation.invitedBy]?.name ?: UNKNOWN_INVITER)
                }
            },
            notifications = notificationRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .map(NotificationResponse::of),
        )
    }

    companion object {
        /** 초대한 사람이 탈퇴한 경우. 초대 자체는 유효하므로 목록에서 빼지 않는다. */
        private const val UNKNOWN_INVITER = "알 수 없음"
    }
}
