package com.duro.kukie.chat.presentation

import com.duro.kukie.chat.exception.ActiveRunExistsException
import com.duro.kukie.chat.exception.ChatNotFoundException
import com.duro.kukie.chat.exception.ClusterRequiredException
import com.duro.kukie.chat.exception.ClusterTeamMismatchException
import com.duro.kukie.chat.presentation.dto.request.CreateChatRequest
import com.duro.kukie.chat.presentation.dto.response.ChatDetailResponse
import com.duro.kukie.chat.presentation.dto.response.ChatResponse
import com.duro.kukie.chat.presentation.dto.response.CreateChatResponse
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.NotTeamAdminException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface ChatControllerDocs {

    @Operation(
        summary = "대화 생성",
        description = "볼 수 있는 클러스터를 골라 대화를 만듭니다. 접속 대상(context·지문)은 클러스터에서 복사하고, " +
            "namespace 를 주지 않으면 클러스터의 기본 namespace 를 씁니다. 제목을 비우면 \"새 대화\" 이고 첫 마디로 지어집니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(ClusterRequiredException::class, ClusterTeamMismatchException::class, ClusterNotFoundException::class)
    fun createChat(userId: UUID, request: CreateChatRequest): ResponseEntity<CreateChatResponse>

    @Operation(
        summary = "대화 목록 조회",
        description = "볼 수 있는 대화를 최근에 바뀐 순으로 조회합니다. clusterId 를 주면 그 클러스터의 대화만 봅니다.",
    )
    fun getChats(userId: UUID, clusterId: UUID?): ResponseEntity<List<ChatResponse>>

    @Operation(
        summary = "대화 조회",
        description = "대화와 실행 상태, 대화 기록(turns, seq 오름차순)을 조회합니다. 조회는 아무것도 바꾸지 않습니다.",
    )
    @ApiErrorResponses(ChatNotFoundException::class)
    fun getChat(conversationId: UUID, userId: UUID): ResponseEntity<ChatDetailResponse>

    @Operation(
        summary = "대화 삭제",
        description = "대화와 그 기록·조치 계획을 지웁니다. private 대화는 만든 사람이, shared 대화는 팀 관리자가 지울 수 있고, " +
            "끝나지 않은 요청이 있으면 지울 수 없습니다. 클러스터의 대화를 모두 지우면 클러스터를 삭제할 수 있습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(ChatNotFoundException::class, NotTeamAdminException::class, ActiveRunExistsException::class)
    fun deleteChat(conversationId: UUID, userId: UUID): ResponseEntity<Unit>
}
