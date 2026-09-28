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
import java.time.LocalDateTime
import java.util.UUID

@Service
class GetInboxService(
    private val teamInvitationRepository: TeamInvitationRepository,
    private val notificationRepository: NotificationRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
) {

    /** 내 주소로 온 유효한 대기 초대와, 읽은 것까지 포함한 내 알림 전부. */
    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): InboxResponse {
        val user = userRepository.findByIdOrThrow(userId)

        val invitations = teamInvitationRepository.findAllByEmailAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            user.email.normalizeEmail(),
            InvitationStatus.PENDING,
            LocalDateTime.now(),
        )
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
        /** 초대한 사람을 못 찾아도 초대는 유효하므로 목록에 남긴다. */
        private const val UNKNOWN_INVITER = "알 수 없음"
    }
}
