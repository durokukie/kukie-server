package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.hosting.exception.HostingAppLimitException
import com.duro.kukie.hosting.exception.HostingAppSourceMismatchException
import com.duro.kukie.hosting.exception.HostingDeployInProgressException
import com.duro.kukie.hosting.exception.HostingDeployNotFoundException
import com.duro.kukie.hosting.exception.HostingDeployNotStartedException
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.hosting.exception.HostingGitHubNotLinkedException
import com.duro.kukie.hosting.exception.HostingInvalidAppNameException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingTooManyDeploysException
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.duro.kukie.hosting.exception.HostingUploadMissingException
import com.duro.kukie.hosting.exception.HostingUploadTooLargeException
import com.duro.kukie.hosting.presentation.dto.request.CreateHostingAgentDeployRequest
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentDeployResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentDeployStatusResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface HostingAgentDeployControllerDocs {

    @Operation(
        summary = "에이전트 배포 만들기",
        description = "폼 name=…&bytes=… 로 부릅니다. 올릴 주소(upload.url, 10분)를 줍니다. 에이전트는 .tar.gz 를 그 주소에 " +
            "PUT(Content-Type: application/gzip)한 뒤 시작을 부릅니다. existing 이 true 면 같은 이름의 업로드 앱이 있고, 이 배포는 그 앱의 " +
            "새 버전입니다. 계정마다 진행 중인 배포는 하나, 한 시간에 10번까지입니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(
        HostingInvalidAppNameException::class,
        HostingUploadTooLargeException::class,
        HostingAppSourceMismatchException::class,
        HostingAppLimitException::class,
        HostingDeployInProgressException::class,
        HostingTooManyDeploysException::class,
        HostingGitHubNotLinkedException::class,
        HostingNotAllowedException::class,
        HostingUnavailableException::class,
        HostingDisabledException::class,
    )
    fun create(userId: UUID, request: CreateHostingAgentDeployRequest): ResponseEntity<HostingAgentDeployResponse>

    @Operation(
        summary = "에이전트 배포 시작",
        description = "올라온 폴더로 배포하라고 배포기에 보냅니다. 같은 배포로 다시 부르면 같은 commandId 를 줍니다. " +
            "실패한 배포를 다시 하려면 새 배포를 만듭니다.",
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    @ApiErrorResponses(
        HostingDeployNotFoundException::class,
        HostingUploadMissingException::class,
        HostingDeployInProgressException::class,
        HostingUnavailableException::class,
    )
    fun start(userId: UUID, deployId: String): ResponseEntity<HostingCommandResponse>

    @Operation(
        summary = "에이전트 배포 결과 기다리기",
        description = "끝나거나 최대 40초(timeout 으로 더 짧게)가 지날 때까지 기다렸다가 상태를 줍니다. done 이 true 가 될 때까지 다시 부릅니다. " +
            "성공하면 url, 실패하면 failure·hint·logTail 이 있습니다. 진행이 30분 멈추거나 대기가 1시간이면 failure.code 가 stuck 입니다.",
    )
    @ApiErrorResponses(HostingDeployNotFoundException::class, HostingDeployNotStartedException::class, HostingUnavailableException::class)
    fun wait(userId: UUID, deployId: String, timeout: Long?): ResponseEntity<HostingAgentDeployStatusResponse>
}
