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

/**
 * 사용자에게 "이런 일이 있었다"고 알리는 한 줄. Inbox 에서 초대와 함께 보인다.
 *
 * 팀 이름은 그때의 값을 그대로 적어 둔다(스냅샷). 알림은 지난 일의 기록이라 팀 이름이 바뀌거나
 * 팀이 사라져도 그대로 남아야 하고, 그래서 팀을 참조하는 외래 키도 두지 않는다.
 *
 * 종류가 하나뿐이라 그 종류에 필요한 값을 컬럼으로 뒀다(전부 nullable). 두 번째 종류가 생기면
 * 그때 payload 를 일반화한다.
 */
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
