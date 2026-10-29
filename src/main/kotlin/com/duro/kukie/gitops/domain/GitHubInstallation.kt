package com.duro.kukie.gitops.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

/** 팀에 묶인 GitHub App 설치. 설치 하나는 한 팀에만 묶인다. */
@Entity
@Table(name = "tbl_github_installation")
class GitHubInstallation(
    teamId: UUID,
    installationId: Long,
    installedBy: UUID,
    account: GitHubAccount,
    repositorySelection: String,
    repositories: List<String>,
    suspended: Boolean,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "team_id", nullable = false)
    var teamId = teamId
        protected set

    @Column(name = "installation_id", nullable = false)
    var installationId = installationId
        protected set

    @Column(name = "installed_by", nullable = false)
    var installedBy = installedBy
        protected set

    @Column(name = "account_login", nullable = false, length = 100)
    var accountLogin = account.login
        protected set

    @Column(name = "account_type", nullable = false, length = 20)
    var accountType = account.type
        protected set

    /** GitHub 의 `all`(계정의 모든 레포) 또는 `selected`(고른 레포만). */
    @Column(name = "repository_selection", nullable = false, length = 20)
    var repositorySelection = repositorySelection
        protected set

    /** 설치가 볼 수 있는 레포의 정규화한 이름. 설치·설정 변경 콜백 때마다 통째로 바꾼다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var repositories: List<String> = repositories
        protected set

    @Column(nullable = false)
    var suspended = suspended
        protected set

    fun refresh(account: GitHubAccount, repositorySelection: String, repositories: List<String>, suspended: Boolean) {
        this.accountLogin = account.login
        this.accountType = account.type
        this.repositorySelection = repositorySelection
        this.repositories = repositories
        this.suspended = suspended
    }
}

data class GitHubAccount(
    val login: String,
    val type: String,
)
