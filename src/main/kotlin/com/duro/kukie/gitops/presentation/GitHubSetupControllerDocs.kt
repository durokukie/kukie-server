package com.duro.kukie.gitops.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity

interface GitHubSetupControllerDocs {

    @Operation(
        summary = "GitHub App Setup URL",
        description = "GitHub 가 설치 뒤 브라우저를 보내는 주소입니다(프록시 밖 주소는 /member/github-app/setup). " +
            "state 를 확인하고 GitHub 에 우리 앱의 설치인지 물은 뒤 팀과 설치를 묶습니다. 설정 변경(update)으로 state 없이 오면 " +
            "이미 묶인 설치의 레포 목록만 다시 읽습니다. 결과는 늘 웹 앱 {WEB_APP_URL}/github-app/setup 으로 302 리다이렉트하며 " +
            "result=installed|updated|requested&teamId=… 또는 result=failed&error=<오류 코드> 를 붙입니다.",
    )
    @ApiResponse(responseCode = "302", description = "웹 앱으로 리다이렉트")
    fun handleGitHubSetup(installationId: String?, setupAction: String?, state: String?): ResponseEntity<Unit>
}
