package com.duro.kukie.gitops.presentation

import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.gitops.exception.GitHubAppNotInstalledException
import com.duro.kukie.gitops.exception.GitHubAppSuspendedException
import com.duro.kukie.gitops.exception.GitHubUnavailableException
import com.duro.kukie.gitops.exception.InvalidRepoUrlException
import com.duro.kukie.gitops.exception.ManifestRepoNotConnectedException
import com.duro.kukie.gitops.presentation.dto.request.ConnectManifestRepoRequest
import com.duro.kukie.gitops.presentation.dto.response.ManifestRepoResponse
import com.duro.kukie.team.exception.NotTeamAdminException
import com.duro.kukie.team.exception.NotTeamMemberException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface ManifestRepoControllerDocs {

    @Operation(summary = "매니페스트 레포 연결 목록", description = "클러스터에 연결한 레포를 연결한 순서로 줍니다. 팀원이면 볼 수 있습니다.")
    @ApiErrorResponses(ClusterNotFoundException::class)
    fun getManifestRepos(clusterId: UUID, userId: UUID): ResponseEntity<List<ManifestRepoResponse>>

    @Operation(
        summary = "매니페스트 레포 연결",
        description = "GitHub 레포 링크(https://github.com/o/r(.git), git@github.com:o/r.git, o/r)를 소문자 owner/repo 로 맞춰 " +
            "클러스터에 연결합니다. 이 팀에 설치한 Kukie GitHub App 이 그 레포를 볼 수 있어야 하며, 아니면 409 " +
            "GITHUB_APP_NOT_INSTALLED 이니 GitHub App 설치를 시작해 권한을 주세요. 이미 연결된 레포면 200 으로 그 연결을 줍니다. " +
            "팀 관리자만 할 수 있습니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(
        InvalidRepoUrlException::class,
        ClusterNotFoundException::class,
        NotTeamMemberException::class,
        NotTeamAdminException::class,
        GitHubAppNotInstalledException::class,
        GitHubAppSuspendedException::class,
        GitHubUnavailableException::class,
    )
    fun connectManifestRepo(clusterId: UUID, userId: UUID, request: ConnectManifestRepoRequest): ResponseEntity<ManifestRepoResponse>

    @Operation(
        summary = "매니페스트 레포 연결 끊기",
        description = "연결을 지웁니다. 그 순간부터 에이전트의 토큰 요청을 거부해 그 레포의 앱은 kubectl 길(경고 후 허용)로 돌아갑니다. " +
            "팀 관리자만 할 수 있습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        ClusterNotFoundException::class,
        NotTeamMemberException::class,
        NotTeamAdminException::class,
        ManifestRepoNotConnectedException::class,
    )
    fun disconnectManifestRepo(clusterId: UUID, repoId: UUID, userId: UUID): ResponseEntity<Unit>
}
