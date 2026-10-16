package com.duro.kukie.chat.presentation

import com.duro.kukie.auth.exception.InvalidInternalTokenException
import com.duro.kukie.chat.exception.ActiveRunExistsException
import com.duro.kukie.chat.exception.RunNotFoundException
import com.duro.kukie.chat.presentation.dto.request.InterruptRunsRequest
import com.duro.kukie.chat.presentation.dto.request.UpdateRunRequest
import com.duro.kukie.chat.presentation.dto.response.InterruptOrphanedRunsResponse
import com.duro.kukie.chat.presentation.dto.response.UpdateRunResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import java.util.UUID

interface InternalRunControllerDocs {

    @Operation(
        summary = "[내부] run 갱신",
        description = "보낸 칸만 바꿉니다 (appendAgentMessages 는 기존 메시지 뒤에 붙입니다). 종료 상태가 되면 끝난 시각을 찍고 " +
            "그 run 의 열린 계획을 같은 트랜잭션에서 닫아 closedPlans 로 줍니다 (승인했는데 결과가 없으면 UNKNOWN, 나머지는 EXPIRED). " +
            "currentMode 는 대화의 모드를, titleFrom 은 첫 마디일 때 대화 제목을 바꿉니다. 사용자 토큰은 보지 않습니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class, RunNotFoundException::class, ActiveRunExistsException::class)
    fun updateRun(runId: UUID, request: UpdateRunRequest): ResponseEntity<UpdateRunResponse>

    @Operation(
        summary = "[내부] 남은 실행 중 run 정리",
        description = "agent 가 기동할 때 부릅니다. running 인 run 을 전부 interrupted 로 닫고 열린 계획도 닫습니다. " +
            "승인 대기·재개 필요 run 은 그대로 둡니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class)
    fun interruptOrphanedRuns(request: InterruptRunsRequest): ResponseEntity<InterruptOrphanedRunsResponse>
}
