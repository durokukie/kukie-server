package com.duro.kukie.inbox.presentation.dto.response

import com.duro.kukie.notification.domain.Notification
import com.duro.kukie.notification.domain.NotificationKind
import com.duro.kukie.team.domain.Team
import com.duro.kukie.team.domain.TeamInvitation
import java.time.LocalDateTime
import java.util.UUID

/** 받은 것들을 한 번에. 앱 사이드바의 받은 초대함이 이 응답 하나로 그려진다. */
data class InboxResponse(
    val invitations: List<InvitationResponse>,
    val notifications: List<NotificationResponse>,
)

data class InvitationResponse(
    val id: UUID,
    val team: InvitedTeamResponse,
    val invitedBy: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun of(invitation: TeamInvitation, team: Team, inviterName: String) = InvitationResponse(
            id = invitation.id,
            team = InvitedTeamResponse(team.id, team.name),
            invitedBy = inviterName,
            createdAt = invitation.createdAt,
        )
    }
}

data class InvitedTeamResponse(
    val id: UUID,
    val name: String,
)

data class NotificationResponse(
    val id: UUID,
    val kind: NotificationKind,
    val payload: Map<String, String>,
    val createdAt: LocalDateTime,
    val readAt: LocalDateTime?,
) {
    companion object {
        fun of(notification: Notification) = NotificationResponse(
            id = notification.id,
            kind = notification.kind,
            payload = buildMap {
                notification.teamId?.let { put("teamId", it.toString()) }
                notification.teamName?.let { put("teamName", it) }
                notification.role?.let { put("role", it.name) }
            },
            createdAt = notification.createdAt,
            readAt = notification.readAt,
        )
    }
}
