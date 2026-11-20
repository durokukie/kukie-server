package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.hosting.exception.HostingCommandTooSoonException
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingUnavailableException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingDbExportsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface HostingDbExportControllerDocs {

    @Operation(
        summary = "팀 DB 내보내기 요청",
        description = "배포기가 팀 DB 를 pg_dump(평문 SQL, gzip)로 내보냅니다. 잠든 DB 는 깨웁니다. 응답의 commandId 가 내보내기 id 이고, " +
            "목록(GET)에 그 id 가 나오면 시작된 것입니다. 다른 배포가 빌드 중이면 몇 분(최대 15분) 걸릴 수 있습니다. " +
            "팀마다 10분에 한 번이고, 더 자주 요청하면 429 입니다. 사용 신청을 하지 않은 계정은 팀 DB 가 없어 403 입니다.",
    )
    @ApiResponse(responseCode = "202", description = "Accepted")
    @ApiErrorResponses(
        HostingNotAllowedException::class,
        HostingCommandTooSoonException::class,
        HostingUnavailableException::class,
        HostingDisabledException::class,
    )
    fun start(userId: UUID): ResponseEntity<HostingCommandResponse>

    @Operation(
        summary = "팀 DB 내보내기 목록",
        description = "최근 내보내기 10건(최신순, 30일 보관)입니다. succeeded 면 downloadUrl 로 .sql.gz 를 받습니다(10분 동안 유효, 목록을 다시 읽으면 새 주소). " +
            "받은 파일은 `gunzip -c 파일.sql.gz | psql <DB 주소>` 로 다른 Postgres 에 넣을 수 있습니다. running 이 30분 넘게 그대로면 failed(stale)로 줍니다.",
    )
    @ApiErrorResponses(HostingUnavailableException::class, HostingDisabledException::class)
    fun getExports(userId: UUID): ResponseEntity<HostingDbExportsResponse>
}
