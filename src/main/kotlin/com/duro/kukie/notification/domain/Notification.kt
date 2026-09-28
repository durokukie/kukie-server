package com.duro.kukie.notification.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.team.domain.TeamRole
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/** 사용자 알림. 팀 이름은 스냅샷으로 적고 팀 FK 를 두지 않는다 — 팀이 바뀌거나 사라져도 기록은 남는다. */
@Entity
@Table(name = "tbl_notification")
class Notification private constructor(
    userId: UUID,
    kind: NotificationKind,
    teamId: UUID?,
    teamName: String?,
    role: TeamRole?,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "user_id", nullable = false)
    var userId = userId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var kind = kind
        protected set

    @Column(name = "team_id")
    var teamId = teamId
        protected set

    @Column(name = "team_name", length = 50)
    var teamName = teamName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    var role = role
        protected set

    @Column(name = "read_at")
    var readAt: LocalDateTime? = null
        protected set

    /** 이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다. */
    fun markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now()
        }
    }

    companion object {
        /** 역할이 바뀐 사람에게 알린다. 역할 변경에는 상대방 승인을 받지 않으므로 알림이 유일한 통지다. */
        fun roleChanged(userId: UUID, teamId: UUID, teamName: String, role: TeamRole) = Notification(
            userId = userId,
            kind = NotificationKind.TEAM_ROLE_CHANGED,
            teamId = teamId,
            teamName = teamName,
            role = role,
        )
    }
}
