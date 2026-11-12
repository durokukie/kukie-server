package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.hosting.exception.HostingAppNotFoundException
import com.duro.kukie.hosting.exception.HostingCommandTooSoonException
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.hosting.exception.HostingLogNotFoundException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingRepositoryNotFoundException
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.duro.kukie.hosting.presentation.dto.request.StartHostingAppRequest
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
            "조직 소유 레포의 앱은 아직 누구에게도 보이지 않습니다. skippedPush 는 배포를 시작하지 못한 마지막 요청이나 push 입니다. " +
            "installation 은 GitHub App 에 읽기를 허용한 레포이고, App 을 설치하지 않았으면 null 입니다. 설치한 뒤 몇 초 지나야 보일 수 있습니다.",
    )
    @ApiErrorResponses(HostingUnavailableException::class, HostingDisabledException::class)
    fun getApps(userId: UUID): ResponseEntity<HostingAppsResponse>

    @Operation(
        summary = "레포를 골라 배포 시작",
        description = "installation.repositories 의 레포 하나로 앱을 시작하라고 배포기에 요청합니다. 기본 브랜치의 최신 커밋을 빌드합니다. " +
            "목록에 latest.commandId 가 응답의 commandId 인 앱이 생기면 시작된 것이고, 시작하지 못했으면 skippedPush 로 알려 줍니다. " +
            "그 뒤로는 그 레포의 기본 브랜치에 push 하면 다시 배포됩니다. 고르지 않은 레포의 push 는 배포하지 않습니다. " +
            "배포 시작은 계정마다 1분에 한 번입니다. 더 자주 요청하면 429 입니다.",
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    @ApiErrorResponses(
        HostingRepositoryNotFoundException::class,
        HostingNotAllowedException::class,
        HostingCommandTooSoonException::class,
        HostingUnavailableException::class,
        HostingDisabledException::class,
    )
    fun start(userId: UUID, request: StartHostingAppRequest): ResponseEntity<HostingCommandResponse>

    @Operation(
        summary = "호스팅 앱 상세",
        description = "앱과 지금 세대의 배포 이력(최신순 20건)을 줍니다. 배포 기록은 30일 뒤 지워지므로 이력이 비어 있어도 app.latest 로 " +
            "마지막 배포를 보여 줄 수 있습니다. 진행 중인 배포의 updatedAt 이 30분 넘게 지났으면 응답 없음으로 봅니다. " +
            "배포의 build 는 어떻게 빌드됐는지입니다. method 가 auto 면 레포에 Dockerfile 이 없어 배포기가 언어와 시작 명령(build.start)을 " +
            "알아낸 것이고, 틀렸으면 레포의 kukie.json 에 start 를 적어 고칩니다. failure.code 에 not_detected(무슨 앱인지 알아내지 못함), " +
            "no_start(시작 명령을 찾지 못함)가 더해졌습니다.",
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
