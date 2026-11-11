package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.hosting.exception.HostingAppNotFoundException
import com.duro.kukie.hosting.exception.HostingCommandTooSoonException
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.hosting.exception.HostingLogNotFoundException
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.duro.kukie.hosting.presentation.dto.response.HostingAppDetailResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAppsResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface HostingControllerDocs {

    @Operation(
        summary = "내 호스팅 앱 목록",
        description = "연결한 GitHub 계정이 주인인 앱을 줍니다. 계정을 연결하지 않았으면 githubLogin 이 null 이고 목록은 비어 있습니다. " +
            "조직 소유 레포의 앱은 아직 누구에게도 보이지 않습니다. skippedPush 는 배포를 시작하지 못한 마지막 push 입니다.",
    )
    @ApiErrorResponses(HostingUnavailableException::class, HostingDisabledException::class)
    fun getApps(userId: UUID): ResponseEntity<HostingAppsResponse>

    @Operation(
        summary = "호스팅 앱 상세",
        description = "앱과 지금 세대의 배포 이력(최신순 20건)을 줍니다. 배포 기록은 30일 뒤 지워지므로 이력이 비어 있어도 app.latest 로 " +
            "마지막 배포를 보여 줄 수 있습니다. 진행 중인 배포의 updatedAt 이 30분 넘게 지났으면 응답 없음으로 봅니다.",
    )
    @ApiErrorResponses(HostingAppNotFoundException::class, HostingUnavailableException::class, HostingDisabledException::class)
    fun getApp(userId: UUID, app: String): ResponseEntity<HostingAppDetailResponse>

    @Operation(
        summary = "빌드 로그",
        description = "배포의 빌드 로그 전체를 text/plain 으로 줍니다. 빌드가 끝난 뒤에만 있습니다(배포의 hasLog). 5MB 를 넘으면 뒤쪽만 있습니다.",
    )
    @ApiErrorResponses(
        HostingAppNotFoundException::class,
        HostingLogNotFoundException::class,
        HostingUnavailableException::class,
        HostingDisabledException::class,
    )
    fun getBuildLog(userId: UUID, app: String, deploymentId: String): ResponseEntity<String>

    @Operation(
        summary = "다시 배포",
        description = "기본 브랜치의 최신 커밋을 다시 배포하라고 배포기에 요청합니다. 바로 시작되지 않을 수 있습니다. 앱의 latest.commandId 가 " +
            "응답의 commandId 가 되면 시작된 것입니다. 같은 앱에 1분 안에 다시 요청하면 429 입니다.",
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    @ApiErrorResponses(
        HostingAppNotFoundException::class,
        HostingCommandTooSoonException::class,
        HostingUnavailableException::class,
        HostingDisabledException::class,
    )
    fun redeploy(userId: UUID, app: String): ResponseEntity<HostingCommandResponse>

    @Operation(
        summary = "호스팅 앱 삭제",
        description = "앱과 이미지를 지우라고 배포기에 요청합니다. 목록에서 앱이 없어지면 끝난 것입니다. 그 전에 같은 레포에 push 한 배포는 " +
            "함께 지워질 수 있습니다.",
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    @ApiErrorResponses(
        HostingAppNotFoundException::class,
        HostingCommandTooSoonException::class,
        HostingUnavailableException::class,
        HostingDisabledException::class,
    )
    fun delete(userId: UUID, app: String): ResponseEntity<HostingCommandResponse>
}
