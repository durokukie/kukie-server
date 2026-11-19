package com.duro.kukie.hosting.presentation

import com.duro.kukie.hosting.presentation.dto.response.HostingAgentKeyResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface HostingAgentKeyControllerDocs {

    @Operation(
        summary = "연결한 에이전트 목록",
        description = "에이전트 로그인을 허용할 때마다 그 컴퓨터의 에이전트가 키를 하나 받습니다. 쓸 수 있는 키만 최근에 쓴 것부터 줍니다. " +
            "30일 쓰지 않은 키는 만료되고(expiresAt), 회원마다 10개까지 둡니다.",
    )
    fun list(userId: UUID): ResponseEntity<List<HostingAgentKeyResponse>>

    @Operation(summary = "에이전트 연결 끊기", description = "그 키는 곧바로 쓸 수 없습니다. 에이전트는 다음 배포 때 로그인을 다시 합니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    fun revoke(userId: UUID, keyId: UUID): ResponseEntity<Unit>
}
