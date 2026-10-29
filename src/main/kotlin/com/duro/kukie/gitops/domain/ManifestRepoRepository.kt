package com.duro.kukie.gitops.domain

import com.duro.kukie.gitops.exception.ManifestRepoNotConnectedException
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ManifestRepoRepository : JpaRepository<ManifestRepo, UUID> {

    fun findAllByClusterIdOrderByCreatedAt(clusterId: UUID): List<ManifestRepo>

    fun findByClusterIdAndFullName(clusterId: UUID, fullName: String): ManifestRepo?

    fun findByClusterIdAndRepositoryId(clusterId: UUID, repositoryId: Long): ManifestRepo?

    fun findByIdAndClusterId(id: UUID, clusterId: UUID): ManifestRepo?

    fun existsByInstallationIdAndRepositoryId(installationId: Long, repositoryId: Long): Boolean
}

fun ManifestRepoRepository.findConnectedOrThrow(clusterId: UUID, repository: RepoName): ManifestRepo =
    findByClusterIdAndFullName(clusterId, repository.fullName) ?: throw ManifestRepoNotConnectedException()
