package com.duro.kukie.gitops.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity

interface GitHubSetupControllerDocs {

    @Operation(
        summary = "GitHub App Setup URL",
        description = "GitHub 가 설치 뒤 브라우저를 보내는 주소입니다(프록시 밖 주소는 /member/github-app/setup). " +
            "state 를 먼저 확인하고(틀리면 GitHub 를 부르지 않습니다) 우리 앱의 설치인지 물은 뒤 팀과 설치를 묶습니다. " +
            "아직 어느 팀에도 묶이지 않은 설치는, 사용자 인증(OAuth)이 켜져 있으면 code 로 설치한 사람이 그 설치에 접근할 수 " +
            "있는지 보고, 꺼져 있으면 state 발급 뒤 10분 안에 새로 생긴 설치만 묶습니다. 설정 변경(update)으로 state 없이 오면 " +
            "아무것도 하지 않습니다(레포 목록은 POST /teams/{teamId}/github-app/refresh). 결과는 늘 웹 앱 " +
            "{WEB_APP_URL}/github-app/setup 으로 302 리다이렉트하며 result=installed|requested&teamId=…, result=updated(&teamId=…) " +
            "또는 result=failed&error=<오류 코드> 를 붙입니다.",
    )
    @ApiResponse(responseCode = "302", description = "웹 앱으로 리다이렉트")
    fun handleGitHubSetup(installationId: String?, setupAction: String?, state: String?, code: String?): ResponseEntity<Unit>
}
