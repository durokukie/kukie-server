package com.duro.kukie.gitops.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.gitops.presentation.dto.response.GitHubAppStatusResponse
import com.duro.kukie.gitops.presentation.dto.response.GitHubInstallLinkResponse
import com.duro.kukie.team.exception.InsufficientTeamRoleException
import com.duro.kukie.team.exception.NotTeamMemberException
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import java.util.UUID

interface GitHubAppControllerDocs {

    @Operation(
        summary = "GitHub App 설치 시작",
        description = "팀에 Kukie GitHub App 을 설치할 GitHub 링크를 줍니다. 앱은 이 링크를 브라우저로 엽니다. " +
            "링크의 state 는 서명했고 30분 안에 한 번만 쓸 수 있습니다. 설치를 마치면 GitHub 가 Setup URL 로 돌려보내고, " +
            "서버가 팀과 설치를 묶은 뒤 웹 앱으로 보냅니다. 팀 관리자만 할 수 있습니다.",
    )
    @ApiErrorResponses(NotTeamMemberException::class, InsufficientTeamRoleException::class)
    fun startGitHubInstall(teamId: UUID, userId: UUID): ResponseEntity<GitHubInstallLinkResponse>

    @Operation(
        summary = "GitHub App 설치 상태",
        description = "팀에 묶인 설치(INSTALLED), 조직 관리자 승인 대기(PENDING), 없음(NONE)과 설치마다 볼 수 있는 레포를 줍니다.",
    )
    @ApiErrorResponses(NotTeamMemberException::class)
    fun getGitHubAppStatus(teamId: UUID): ResponseEntity<GitHubAppStatusResponse>
}
