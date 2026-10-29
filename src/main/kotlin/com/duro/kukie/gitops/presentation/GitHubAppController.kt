package com.duro.kukie.gitops.presentation

import com.duro.kukie.gitops.application.GetGitHubAppStatusService
import com.duro.kukie.gitops.application.RefreshGitHubAppService
import com.duro.kukie.gitops.application.StartGitHubInstallService
import com.duro.kukie.gitops.presentation.dto.response.GitHubAppStatusResponse
import com.duro.kukie.gitops.presentation.dto.response.GitHubInstallLinkResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.presentation.TeamRoleRequired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/teams/{teamId}/github-app")
class GitHubAppController(
    private val startGitHubInstallService: StartGitHubInstallService,
    private val getGitHubAppStatusService: GetGitHubAppStatusService,
    private val refreshGitHubAppService: RefreshGitHubAppService,
) : GitHubAppControllerDocs {

    @TeamRoleRequired(TeamRole.ADMIN)
    @PostMapping("/installations")
    override fun startGitHubInstall(
        @PathVariable teamId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<GitHubInstallLinkResponse> {
        return ResponseEntity.ok(startGitHubInstallService(teamId, userId))
    }

    @TeamRoleRequired(TeamRole.MEMBER)
    @GetMapping
    override fun getGitHubAppStatus(
        @PathVariable teamId: UUID,
    ): ResponseEntity<GitHubAppStatusResponse> {
        return ResponseEntity.ok(getGitHubAppStatusService(teamId))
    }

    @TeamRoleRequired(TeamRole.ADMIN)
    @PostMapping("/refresh")
    override fun refreshGitHubApp(
        @PathVariable teamId: UUID,
    ): ResponseEntity<GitHubAppStatusResponse> {
        return ResponseEntity.ok(refreshGitHubAppService(teamId))
    }
}
