package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.hosting.exception.HostingCommandTooSoonException
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.hosting.exception.HostingGitHubLoginChangedException
import com.duro.kukie.hosting.exception.HostingNoticeNotAgreedException
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.duro.kukie.hosting.presentation.dto.request.JoinHostingRequest
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface HostingMembershipControllerDocs {

    @Operation(
        summary = "호스팅 사용 신청",
        description = "호스팅을 쓰겠다고 신청합니다. GitHub 를 연결했으면 그 계정으로, 아니면 Kukie 계정으로 가입합니다. " +
            "화면은 먼저 목록의 closesOn(서비스 종료일, 그날 앱과 데이터가 지워짐)을 보여 주고 동의를 받아 agreed 를 true 로 보냅니다. 연결한 뒤 GitHub 계정 이름을 바꿨으면 409 HOSTING_GITHUB_LOGIN_CHANGED 입니다. 통과하면 배포기에 가입을 요청하고, 목록의 joined 가 true 가 되면 가입된 것입니다. " +
            "가입하지 못했으면 목록의 skippedPush.commandId 가 응답의 commandId 입니다. 신청은 계정마다 1분에 한 번이고, 거절된 신청도 셉니다.",
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    @ApiErrorResponses(
        HostingNoticeNotAgreedException::class,
        HostingGitHubLoginChangedException::class,
        HostingCommandTooSoonException::class,
        HostingUnavailableException::class,
        HostingDisabledException::class,
    )
    fun join(userId: UUID, request: JoinHostingRequest): ResponseEntity<HostingCommandResponse>
}
