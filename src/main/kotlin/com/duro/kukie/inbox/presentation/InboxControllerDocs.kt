package com.duro.kukie.inbox.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.inbox.presentation.dto.response.InboxResponse
import com.duro.kukie.notification.exception.NotMyNotificationException
import com.duro.kukie.notification.exception.NotificationNotFoundException
import com.duro.kukie.team.exception.AlreadyTeamMemberException
import com.duro.kukie.team.exception.InvitationNotFoundException
import com.duro.kukie.team.exception.InvitationNotPendingException
import com.duro.kukie.team.exception.NotMyInvitationException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface InboxControllerDocs {

    @Operation(summary = "받은 초대함 조회", description = "대기 중인 팀 초대와 내 알림을 조회합니다. 초대는 아직 처리하지 않은 것만, 알림은 읽은 것도 함께 옵니다.")
    fun getInbox(userId: UUID): ResponseEntity<InboxResponse>

    @Operation(summary = "팀 초대 수락", description = "초대를 수락해 팀 구성원이 됩니다. 자신에게 온 초대만 수락할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        InvitationNotFoundException::class,
        NotMyInvitationException::class,
        InvitationNotPendingException::class,
        AlreadyTeamMemberException::class,
    )
    fun acceptInvitation(invitationId: UUID, userId: UUID): ResponseEntity<Unit>

    @Operation(summary = "팀 초대 거절", description = "초대를 거절합니다. 자신에게 온 초대만 거절할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        InvitationNotFoundException::class,
        NotMyInvitationException::class,
        InvitationNotPendingException::class,
    )
    fun declineInvitation(invitationId: UUID, userId: UUID): ResponseEntity<Unit>

    @Operation(summary = "알림 읽음 처리", description = "알림을 읽음으로 표시합니다. 자신의 알림만 처리할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(NotificationNotFoundException::class, NotMyNotificationException::class)
    fun readNotification(notificationId: UUID, userId: UUID): ResponseEntity<Unit>
}
