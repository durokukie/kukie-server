package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.hosting.exception.HostingLogNotFoundException
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.duro.kukie.hosting.presentation.dto.response.HostingHistoryResponse
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import java.util.UUID

interface HostingHistoryControllerDocs {

    @Operation(
        summary = "지운 앱의 배포 이력",
        description = "지운 앱과, 지우고 같은 이름으로 다시 만든 앱의 그 전 세대의 배포 기록입니다(최근 배포순 20개, 앱마다 20건). " +
            "배포 기록은 앱을 지워도 30일 동안 남습니다. 배포 파일에 주인이 적히기 전(2026-11-20) 기록은 GitHub 계정 팀이면 보이지 않습니다.",
    )
    @ApiErrorResponses(HostingUnavailableException::class, HostingDisabledException::class)
    fun getHistory(userId: UUID): ResponseEntity<HostingHistoryResponse>

    @Operation(
        summary = "지운 앱의 빌드 로그",
        description = "배포 이력(GET /hosting/history)에 나온 배포의 빌드 로그 전체를 text/plain 으로 줍니다. 배포의 hasLog 가 true 일 때만 있습니다.",
    )
    @ApiErrorResponses(HostingLogNotFoundException::class, HostingUnavailableException::class, HostingDisabledException::class)
    fun getBuildLog(userId: UUID, app: String, generation: String, deploymentId: String): ResponseEntity<String>
}
