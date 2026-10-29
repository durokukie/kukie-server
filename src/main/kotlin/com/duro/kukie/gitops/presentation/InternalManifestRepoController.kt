package com.duro.kukie.gitops.presentation

import com.duro.kukie.gitops.application.GetClusterManifestReposService
import com.duro.kukie.gitops.application.IssueRepoTokenService
import com.duro.kukie.gitops.presentation.dto.request.IssueRepoTokenRequest
import com.duro.kukie.gitops.presentation.dto.response.ManifestRepoResponse
import com.duro.kukie.gitops.presentation.dto.response.RepoTokenResponse
import com.duro.kukie.global.security.InternalApi
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@InternalApi
@RestController
@RequestMapping("/internal/clusters/{clusterId}/manifest-repos")
class InternalManifestRepoController(
    private val getClusterManifestReposService: GetClusterManifestReposService,
    private val issueRepoTokenService: IssueRepoTokenService,
) : InternalManifestRepoControllerDocs {

    @GetMapping
    override fun getClusterManifestRepos(
        @PathVariable clusterId: UUID,
    ): ResponseEntity<List<ManifestRepoResponse>> {
        return ResponseEntity.ok(getClusterManifestReposService(clusterId))
    }

    @PostMapping("/token")
    override fun issueRepoToken(
        @PathVariable clusterId: UUID,
        @RequestBody @Valid request: IssueRepoTokenRequest,
    ): ResponseEntity<RepoTokenResponse> {
        return ResponseEntity.ok(issueRepoTokenService(request.toCommand(clusterId)))
    }
}
