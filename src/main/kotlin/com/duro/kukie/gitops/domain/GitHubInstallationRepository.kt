package com.duro.kukie.gitops.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GitHubInstallationRepository : JpaRepository<GitHubInstallation, UUID> {

    fun findByInstallationId(installationId: Long): GitHubInstallation?

    fun findAllByTeamIdOrderByCreatedAt(teamId: UUID): List<GitHubInstallation>
}
