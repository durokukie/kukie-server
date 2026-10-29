package com.duro.kukie.gitops.presentation

import com.duro.kukie.auth.exception.InvalidInternalTokenException
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.gitops.exception.GitHubAppNotInstalledException
import com.duro.kukie.gitops.exception.GitHubAppSuspendedException
import com.duro.kukie.gitops.exception.GitHubUnavailableException
import com.duro.kukie.gitops.exception.InvalidRepoUrlException
import com.duro.kukie.gitops.exception.ManifestRepoNotConnectedException
import com.duro.kukie.gitops.presentation.dto.request.IssueRepoTokenRequest
import com.duro.kukie.gitops.presentation.dto.response.ManifestRepoResponse
import com.duro.kukie.gitops.presentation.dto.response.RepoTokenResponse
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import java.util.UUID

interface InternalManifestRepoControllerDocs {

    @Operation(summary = "[내부] 클러스터의 매니페스트 레포", description = "클러스터에 연결된 레포(소문자 owner/repo)를 줍니다.")
    @ApiErrorResponses(InvalidInternalTokenException::class, ClusterNotFoundException::class)
    fun getClusterManifestRepos(clusterId: UUID): ResponseEntity<List<ManifestRepoResponse>>

    @Operation(
        summary = "[내부] 레포 설치 토큰 발급",
        description = "연결된 레포 하나로만 범위를 좁힌 GitHub App 설치 토큰(1시간)을 줍니다. repository 는 owner/repo 나 " +
            "ArgoCD repoURL 그대로 받습니다. 연결되지 않았거나 끊긴 레포면 404 MANIFEST_REPO_NOT_CONNECTED 입니다. " +
            "남은 시간이 30분보다 길면 같은 토큰을 다시 줍니다 — 쓸 때마다 받아 쓰세요. 토큰은 로그에 남기지 마세요.",
    )
    @ApiErrorResponses(
        InvalidInternalTokenException::class,
        InvalidRepoUrlException::class,
        ClusterNotFoundException::class,
        ManifestRepoNotConnectedException::class,
        GitHubAppNotInstalledException::class,
        GitHubAppSuspendedException::class,
        GitHubUnavailableException::class,
    )
    fun issueRepoToken(clusterId: UUID, request: IssueRepoTokenRequest): ResponseEntity<RepoTokenResponse>
}
