package com.duro.kukie.gitops.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GitHubInstallRequestRepository : JpaRepository<GitHubInstallRequest, UUID>
