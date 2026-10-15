package com.duro.kukie.cluster.presentation

import com.duro.kukie.auth.exception.InvalidInternalTokenException
import com.duro.kukie.cluster.exception.ClusterChangedException
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.cluster.presentation.dto.request.UpdateClusterStatusRequest
import com.duro.kukie.cluster.presentation.dto.response.ClusterConnectionResponse
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import java.util.UUID

interface InternalClusterControllerDocs {

    @Operation(
        summary = "[내부] 클러스터 연결 정보",
        description = "agent 가 kubectl 을 실행하기 직전에 평문 자격증명까지 받아 갑니다. 사용자에게 읽기 권한이 있어야 합니다. " +
            "expectedFingerprint 가 지금 지문과 다르면 409 입니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class, ClusterNotFoundException::class, ClusterChangedException::class)
    fun getClusterConnection(clusterId: UUID, userId: UUID, expectedFingerprint: String?): ResponseEntity<ClusterConnectionResponse>

    @Operation(summary = "[내부] 클러스터 상태 갱신", description = "agent 의 연결 확인 결과를 남깁니다. 사용자 토큰은 보지 않습니다.")
    @ApiErrorResponses(InvalidInternalTokenException::class, ClusterNotFoundException::class)
    fun updateClusterStatus(clusterId: UUID, request: UpdateClusterStatusRequest): ResponseEntity<ClusterResponse>
}
