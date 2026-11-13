package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.hosting.exception.HostingAgentDeniedException
import com.duro.kukie.hosting.exception.HostingAgentExpiredException
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.hosting.exception.HostingGitHubNotLinkedException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingTooManyRequestsException
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentLoginRequestResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentLoginResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface HostingAgentControllerDocs {

    @Operation(
        summary = "에이전트 로그인 시작",
        description = "코딩 에이전트(Claude Code, Codex)가 부릅니다. 인증이 없습니다. 에이전트는 code 와 loginUrl 을 사용자에게 보여 주고, " +
            "사용자는 loginUrl 에서 code 를 입력해 허용합니다. 에이전트는 pollToken 으로 토큰을 받습니다. 10분 뒤 만료됩니다.",
    )
    @ApiErrorResponses(HostingTooManyRequestsException::class)
    fun startLogin(): ResponseEntity<HostingAgentLoginResponse>

    @Operation(
        summary = "에이전트 토큰 받기",
        description = "폼 본문 pollToken=… 으로 부릅니다. 허용될 때까지 최대 25초 기다립니다. 허용되면 200 과 text/plain 한 줄 " +
            "`Authorization: Bearer <토큰>`을 줍니다. 에이전트는 이 응답을 파일로 받아 `curl -H @파일`로 씁니다. 토큰은 1시간짜리이고 " +
            "/hosting/agent 아래의 에이전트 API 에서만 받습니다. 아직이면 202 와 {\"status\":\"pending\"}, 곧바로 다시 부릅니다. " +
            "토큰은 한 번만 줍니다. 응답을 놓쳤으면 로그인을 처음부터 다시 합니다.",
    )
    @ApiResponse(responseCode = "202", description = "Accepted: 아직 허용되지 않음")
    @ApiErrorResponses(HostingAgentDeniedException::class, HostingNotAllowedException::class, HostingAgentExpiredException::class)
    fun token(pollToken: String): ResponseEntity<Any>

    @Operation(
        summary = "에이전트 로그인 요청 보기",
        description = "웹 허용 화면이 사용자가 입력한 코드의 요청을 보여 줄 때 부릅니다. 대소문자와 하이픈은 가리지 않습니다. " +
            "requestedAt 을 보여 주어 지금 자기가 시작한 요청인지 확인하게 합니다.",
    )
    @ApiErrorResponses(HostingAgentExpiredException::class, HostingTooManyRequestsException::class)
    fun getLogin(userId: UUID, code: String): ResponseEntity<HostingAgentLoginRequestResponse>

    @Operation(
        summary = "에이전트 로그인 허용",
        description = "그 에이전트가 1시간 동안 내 GitHub 계정으로 호스팅 앱을 배포할 수 있게 합니다. GitHub 계정을 연결하지 않았으면 409, " +
            "아직 호스팅을 쓸 수 없는 계정이면 403 이고 에이전트도 그렇게 알게 됩니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        HostingAgentExpiredException::class,
        HostingGitHubNotLinkedException::class,
        HostingNotAllowedException::class,
        HostingUnavailableException::class,
        HostingDisabledException::class,
    )
    fun approve(userId: UUID, code: String): ResponseEntity<Unit>

    @Operation(summary = "에이전트 로그인 거부", description = "에이전트는 거부됐다는 답을 받고 멈춥니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(HostingAgentExpiredException::class)
    fun deny(userId: UUID, code: String): ResponseEntity<Unit>
}
