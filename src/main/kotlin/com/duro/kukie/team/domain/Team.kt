package com.duro.kukie.team.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * 구성원이 함께 Kubernetes Cluster를 관리하는 최상위 협업 단위. Slack의 Workspace에 해당한다.
 * 한 사용자는 여러 Team에 속할 수 있고, 하나의 Cluster는 하나의 Team에만 속한다.
 */
@Entity
@Table(name = "tbl_team")
class Team(
    name: String,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(nullable = false, length = 50)
    var name = name
        protected set

    fun rename(name: String) {
        this.name = name
    }
}
