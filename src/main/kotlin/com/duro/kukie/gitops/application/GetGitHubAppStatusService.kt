package com.duro.kukie.gitops.application

import com.duro.kukie.gitops.domain.GitHubInstallRequestRepository
import com.duro.kukie.gitops.domain.GitHubInstallationRepository
import com.duro.kukie.gitops.presentation.dto.response.GitHubAppStatusResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetGitHubAppStatusService(
    private val gitHubInstallationRepository: GitHubInstallationRepository,
    private val gitHubInstallRequestRepository: GitHubInstallRequestRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(teamId: UUID): GitHubAppStatusResponse =
        GitHubAppStatusResponse.of(
            installations = gitHubInstallationRepository.findAllByTeamIdOrderByCreatedAt(teamId),
            request = gitHubInstallRequestRepository.findByIdOrNull(teamId),
        )
}
