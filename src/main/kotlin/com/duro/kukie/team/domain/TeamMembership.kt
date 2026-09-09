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

/**
 * 어떤 사용자가 어떤 팀에 어떤 역할로 속해 있는지. 팀과 회원의 연결 자체가 이 엔티티다.
 *
 * 연관관계 대신 id만 들고 있다. 회원은 Spring 회원 서버(tbl_user)의 것이고, 팀 기능이
 * 회원 엔티티를 직접 끌어오면 두 도메인이 얽힌다.
 */
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
