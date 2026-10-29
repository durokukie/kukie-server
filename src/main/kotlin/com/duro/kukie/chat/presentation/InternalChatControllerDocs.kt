package com.duro.kukie.chat.presentation

import com.duro.kukie.auth.exception.InvalidInternalTokenException
import com.duro.kukie.chat.exception.ActiveRunExistsException
import com.duro.kukie.chat.exception.ChatNotFoundException
import com.duro.kukie.chat.exception.PrivateSessionException
import com.duro.kukie.chat.exception.RequestMismatchException
import com.duro.kukie.chat.exception.RunNotFoundException
import com.duro.kukie.chat.presentation.dto.request.InterruptRunsRequest
import com.duro.kukie.chat.presentation.dto.request.StartRunRequest
import com.duro.kukie.chat.presentation.dto.request.UpdateRepoLinkStateRequest
import com.duro.kukie.chat.presentation.dto.response.ChatContextResponse
import com.duro.kukie.chat.presentation.dto.response.InterruptRunsResponse
import com.duro.kukie.chat.presentation.dto.response.RepoLinkStateResponse
import com.duro.kukie.chat.presentation.dto.response.RunResponse
import com.duro.kukie.chat.presentation.dto.response.StartRunResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.NotTeamAdminException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface InternalChatControllerDocs {

    @Operation(
        summary = "[내부] 대화 컨텍스트",
        description = "agent 가 대화 하나를 열 때 필요한 것(방, 활성 run 과 그 계획, 완료 run 의 메시지 최근 100개)을 한 번에 줍니다. " +
            "purpose=chat 은 대화 읽기 권한을, purpose=approve 는 승인·재개 권한까지 봅니다.",
    )
    @ApiErrorResponses(
        InvalidInternalTokenException::class,
        ChatNotFoundException::class,
        PrivateSessionException::class,
        NotTeamAdminException::class,
    )
    fun getChatContext(conversationId: UUID, userId: UUID, purpose: String): ResponseEntity<ChatContextResponse>

    @Operation(summary = "[내부] requestId 로 run 찾기", description = "같은 requestId 로 이미 만든 run 을 줍니다.")
    @ApiErrorResponses(InvalidInternalTokenException::class, ChatNotFoundException::class, RunNotFoundException::class)
    fun findRunByRequest(conversationId: UUID, requestId: String, userId: UUID): ResponseEntity<RunResponse>

    @Operation(
        summary = "[내부] run 시작",
        description = "대화 행을 잠그고 run 을 만듭니다. 같은 requestId·같은 입력의 재전송이면 기존 run 을 200 으로 줍니다. " +
            "같은 requestId 에 입력이 다르면, 또는 끝나지 않은 run 이 있으면 409 입니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(
        InvalidInternalTokenException::class,
        ChatNotFoundException::class,
        RequestMismatchException::class,
        ActiveRunExistsException::class,
    )
    fun startRun(conversationId: UUID, userId: UUID, request: StartRunRequest): ResponseEntity<StartRunResponse>

    @Operation(
        summary = "[내부] 대화 중단",
        description = "대화의 활성 run 을 interrupted 로 닫고 그 run 의 열린 계획도 함께 닫습니다. 사용자 토큰은 보지 않습니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class, ChatNotFoundException::class)
    fun interruptChat(conversationId: UUID, request: InterruptRunsRequest): ResponseEntity<InterruptRunsResponse>

    @Operation(
        summary = "[내부] 마지막으로 본 레포 연결 상태 읽기",
        description = "대화마다 에이전트만 쓰는 숨은 칸입니다. 지금 연결 상태와 비교해 바뀐 때만 기록 끝에 메모를 넣는 데 씁니다. " +
            "한 번도 저장하지 않았으면 state 가 null 입니다. 대화 컨텍스트의 conversation.repoLinkState 와 같은 값입니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class, ChatNotFoundException::class)
    fun getRepoLinkState(conversationId: UUID): ResponseEntity<RepoLinkStateResponse>

    @Operation(
        summary = "[내부] 마지막으로 본 레포 연결 상태 저장",
        description = "state 를 통째로 덮어씁니다(값 모양은 에이전트가 정합니다). 대화 목록 순서(updatedAt)는 바꾸지 않습니다.",
    )
    @ApiErrorResponses(InvalidInternalTokenException::class, ChatNotFoundException::class)
    fun updateRepoLinkState(conversationId: UUID, request: UpdateRepoLinkStateRequest): ResponseEntity<RepoLinkStateResponse>
}
