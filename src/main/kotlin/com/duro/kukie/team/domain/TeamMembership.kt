package com.duro.kukie.team.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/** 사용자·팀·역할의 연결. 도메인이 얽히지 않게 연관관계 대신 id 만 든다. */
@Entity
@Table(
    name = "tbl_team_membership",
    uniqueConstraints = [UniqueConstraint(name = "uk_team_membership_team_user", columnNames = ["team_id", "user_id"])],
)
class TeamMembership(
    teamId: UUID,
    userId: UUID,
    role: TeamRole,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "team_id", nullable = false)
    var teamId = teamId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId = userId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var role = role
        protected set

    fun changeRole(role: TeamRole) {
        this.role = role
    }
}
