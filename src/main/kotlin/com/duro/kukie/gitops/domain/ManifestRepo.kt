package com.duro.kukie.gitops.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** 클러스터의 ArgoCD 가 보는 매니페스트 레포 연결. 레포는 이름이 바뀌어도 따라가도록 GitHub 레포 id 로도 잡는다. */
@Entity
@Table(name = "tbl_manifest_repo")
class ManifestRepo(
    teamId: UUID,
    clusterId: UUID,
    installationId: Long,
    repositoryId: Long,
    repository: RepoName,
    connectedBy: UUID,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "team_id", nullable = false)
    var teamId = teamId
        protected set

    @Column(name = "cluster_id", nullable = false)
    var clusterId = clusterId
        protected set

    @Column(name = "installation_id", nullable = false)
    var installationId = installationId
        protected set

    @Column(name = "repository_id", nullable = false)
    var repositoryId = repositoryId
        protected set

    /** 정규화한 `owner/repo`. */
    @Column(name = "full_name", nullable = false, length = 200)
    var fullName = repository.fullName
        protected set

    @Column(name = "connected_by", nullable = false)
    var connectedBy = connectedBy
        protected set
}
