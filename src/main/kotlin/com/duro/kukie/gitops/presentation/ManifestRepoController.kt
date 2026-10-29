package com.duro.kukie.gitops.presentation

import com.duro.kukie.gitops.application.ConnectManifestRepoService
import com.duro.kukie.gitops.application.DisconnectManifestRepoService
import com.duro.kukie.gitops.application.GetManifestReposService
import com.duro.kukie.gitops.presentation.dto.request.ConnectManifestRepoRequest
import com.duro.kukie.gitops.presentation.dto.response.ManifestRepoResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/clusters/{clusterId}/manifest-repos")
class ManifestRepoController(
    private val getManifestReposService: GetManifestReposService,
    private val connectManifestRepoService: ConnectManifestRepoService,
    private val disconnectManifestRepoService: DisconnectManifestRepoService,
) : ManifestRepoControllerDocs {

    @GetMapping
    override fun getManifestRepos(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<List<ManifestRepoResponse>> {
        return ResponseEntity.ok(getManifestReposService(clusterId, userId))
    }

    @PostMapping
    override fun connectManifestRepo(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: ConnectManifestRepoRequest,
    ): ResponseEntity<ManifestRepoResponse> {
        val result = connectManifestRepoService(request.toCommand(clusterId, userId))
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK

        return ResponseEntity.status(status).body(result.repo)
    }

    @DeleteMapping("/{repoId}")
    override fun disconnectManifestRepo(
        @PathVariable clusterId: UUID,
        @PathVariable repoId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        disconnectManifestRepoService(clusterId, repoId, userId)

        return ResponseEntity.noContent().build()
    }
}
