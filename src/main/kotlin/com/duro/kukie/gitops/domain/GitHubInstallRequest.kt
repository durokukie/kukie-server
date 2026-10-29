package com.duro.kukie.gitops.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/** 조직 관리자의 승인을 기다리는 설치 요청. GitHub 는 이때 installation_id 를 주지 않아 팀만 적어 둔다. */
@Entity
@Table(name = "tbl_github_install_request")
class GitHubInstallRequest(
    teamId: UUID,
    requestedBy: UUID,
    requestedAt: LocalDateTime,
) {

    @Id
    @Column(name = "team_id")
    var teamId = teamId
        protected set

    @Column(name = "requested_by", nullable = false)
    var requestedBy = requestedBy
        protected set

    @Column(name = "requested_at", nullable = false)
    var requestedAt = requestedAt
        protected set
}
