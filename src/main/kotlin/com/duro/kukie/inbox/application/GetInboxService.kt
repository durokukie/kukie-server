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
     * 대기 중인 초대와 내 알림 전부.
     *
     * **초대와 알림의 기준이 다르다.** 초대는 PENDING 만 — 수락·거절하면 할 일이 없다. 알림은 읽은
     * 것까지 함께 준다. 읽음 여부(`readAt`)를 응답에 실어 화면이 구분해 그리기 때문이다.
     * 알림에는 아직 개수 상한이 없다 — 오래 쓰면 응답이 계속 커진다 (후속 과제, 자동 리뷰 지적).
     *
     * 초대는 이메일 주소로 저장되므로 내 주소로 온 것을 찾는다 — 초대받은 뒤에 가입한 사람도 그래서
     * 자기 초대를 보게 된다 (제품기획서 02 §4).
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
        /**
         * 초대한 사람을 찾지 못한 경우. 초대 자체는 유효하므로 목록에서 빼지 않는다.
         *
         * `invited_by` 는 NOT NULL + `tbl_user` FK 라 **지금은 여기에 닿지 않는다.** 탈퇴 기능이
         * 생기면(FK 를 nullable + ON DELETE SET NULL 로 바꾸면) 그때 쓰인다 (자동 리뷰 지적).
         */
        private const val UNKNOWN_INVITER = "알 수 없음"
    }
}
