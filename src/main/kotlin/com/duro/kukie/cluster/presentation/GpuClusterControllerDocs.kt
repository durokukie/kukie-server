package com.duro.kukie.cluster.presentation

import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.exception.EksClusterNotFoundException
import com.duro.kukie.cluster.exception.GpuAddonNotInstalledException
import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.exception.GpuNodeControlDeniedException
import com.duro.kukie.cluster.exception.GpuNodeGroupNotFoundException
import com.duro.kukie.cluster.exception.GpuNotSupportedException
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.exception.InvalidNodeCountException
import com.duro.kukie.cluster.exception.ModelChatBusyException
import com.duro.kukie.cluster.exception.ModelChatTooLargeException
import com.duro.kukie.cluster.exception.ModelNotReadyException
import com.duro.kukie.cluster.exception.NodeGroupBusyException
import com.duro.kukie.cluster.exception.ServedModelExistsException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import com.duro.kukie.cluster.presentation.dto.request.CreateServedModelRequest
import com.duro.kukie.cluster.presentation.dto.request.ExtendServedModelRequest
import com.duro.kukie.cluster.presentation.dto.request.ModelChatRequest
import com.duro.kukie.cluster.presentation.dto.request.UpdateGpuNodeGroupRequest
import com.duro.kukie.cluster.presentation.dto.response.ExtendServedModelResponse
import com.duro.kukie.cluster.presentation.dto.response.MeasureServedModelResponse
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupResponse
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupsResponse
import com.duro.kukie.cluster.presentation.dto.response.ServedModelResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.NotTeamAdminException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

interface GpuClusterControllerDocs {

    @Operation(
        summary = "GPU 노드 그룹 조회",
        description = "EKS 클러스터의 GPU 노드 그룹(인스턴스 종류가 g·p 뒤에 숫자)을 조회합니다. EKS 가 아니면 supported 가 false 이고 목록은 비어 있습니다. " +
            "control 은 노드 수 변경 권한으로, 스택의 AccessLevel 이 full, GpuNodeControl 이 true 이고 클러스터 이름이 GpuClusterPattern 에 맞을 때만 true 입니다. " +
            "모델 시작·중지는 control 을 보지 않습니다. " +
            "고객 역할이 템플릿 v3 이전이라 노드 그룹을 읽지 못하면 EKS_ACCESS_DENIED 입니다.",
    )
    @ApiErrorResponses(
        ClusterNotFoundException::class,
        AwsRoleNotAssumableException::class,
        EksClusterNotFoundException::class,
        EksAccessDeniedException::class,
        GpuClusterApiFailedException::class,
    )
    fun getGpuNodeGroups(clusterId: UUID, userId: UUID): ResponseEntity<GpuNodeGroupsResponse>

    @Operation(
        summary = "GPU 노드 수 변경",
        description = "GPU 노드 그룹의 desired 를 바꿉니다. 팀 관리자만 할 수 있고 control 이 true 여야 합니다. min·max 값은 바꾸지 않습니다. " +
            "desired 는 min 이상 max 이하여야 합니다. 이미 그 값이면 AWS 를 부르지 않고 지금 상태를 돌려줍니다.",
    )
    @ApiErrorResponses(
        GpuNotSupportedException::class,
        InvalidNodeCountException::class,
        GpuNodeControlDeniedException::class,
        NotTeamAdminException::class,
        ClusterNotFoundException::class,
        GpuNodeGroupNotFoundException::class,
        NodeGroupBusyException::class,
        AwsRoleNotAssumableException::class,
        GpuClusterApiFailedException::class,
    )
    fun updateGpuNodeGroup(
        clusterId: UUID,
        name: String,
        userId: UUID,
        request: UpdateGpuNodeGroupRequest,
    ): ResponseEntity<GpuNodeGroupResponse>

    @Operation(
        summary = "모델 시작",
        description = "llm namespace 에 ServedModel 을 만들어 모델을 내려받고 서빙을 시작합니다. 팀 관리자만 할 수 있고 스택의 AccessLevel 이 full 이어야 합니다. " +
            "limited 이거나 연결을 해제했으면 GPU_SERVING_DENIED 입니다. " +
            "클러스터에 모델 하나이므로 그 namespace 에 ServedModel 이 이미 있으면 SERVED_MODEL_EXISTS 입니다. " +
            "gpus 는 1, 2, 4, 8 중 하나이고 그 밖의 값은 400 입니다. 없으면 1 입니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(
        GpuNotSupportedException::class,
        GpuServingDeniedException::class,
        NotTeamAdminException::class,
        ClusterNotFoundException::class,
        ServedModelExistsException::class,
        GpuAddonNotInstalledException::class,
        GpuClusterApiFailedException::class,
        AwsRoleNotAssumableException::class,
    )
    fun createServedModel(clusterId: UUID, userId: UUID, request: CreateServedModelRequest): ResponseEntity<ServedModelResponse>

    @Operation(
        summary = "모델 중지",
        description = "llm namespace 의 ServedModel 을 지웁니다. 팀 관리자만 할 수 있고 스택의 AccessLevel 이 full 이어야 합니다. " +
            "limited 이거나 연결을 해제했으면 GPU_SERVING_DENIED 입니다. 내려받은 모델 파일은 남습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        GpuNotSupportedException::class,
        GpuServingDeniedException::class,
        NotTeamAdminException::class,
        ClusterNotFoundException::class,
        ServedModelNotFoundException::class,
        GpuClusterApiFailedException::class,
        AwsRoleNotAssumableException::class,
    )
    fun deleteServedModel(clusterId: UUID, name: String, userId: UUID): ResponseEntity<Unit>

    @Operation(
        summary = "모델과 대화",
        description = "phase 가 Ready 인 모델에 메시지를 보내고 답을 SSE 로 받습니다. 팀 관리자만 할 수 있고 스택의 AccessLevel 이 full 이어야 합니다. " +
            "대화는 저장하지 않으며 내용을 로그에 남기지 않습니다. maxTokens 는 기본 512, 최대 2048 이고 본문은 1MiB 까지입니다. 사용자당 동시에 1건입니다. " +
            "시작 전에 알 수 있는 거절은 일반 JSON 오류이고, 시작한 뒤의 실패는 error 이벤트입니다. " +
            "이벤트: delta {content, reasoning}, done {usage, ttftMs, tokensPerSecond, finishReason}, error {code, message}. " +
            "error 의 code 는 GPU_SERVING_DENIED, MODEL_NOT_READY, MODEL_CHAT_BUSY, MODEL_CHAT_REJECTED, MODEL_CHAT_TIMEOUT, GPU_CLUSTER_API_FAILED 등입니다. " +
            "done 이나 error 없이 스트림이 끝나면 연결이 끊긴 것입니다. 콜론으로 시작하는 줄은 keep-alive 이므로 버립니다.",
    )
    @ApiResponse(responseCode = "200", description = "OK", content = [Content(mediaType = "text/event-stream")])
    @ApiErrorResponses(
        GpuNotSupportedException::class,
        GpuServingDeniedException::class,
        NotTeamAdminException::class,
        ClusterNotFoundException::class,
        ServedModelNotFoundException::class,
        ModelNotReadyException::class,
        ModelChatBusyException::class,
        ModelChatTooLargeException::class,
        GpuClusterApiFailedException::class,
        AwsRoleNotAssumableException::class,
    )
    fun chatWithServedModel(clusterId: UUID, name: String, userId: UUID, request: ModelChatRequest): ResponseEntity<SseEmitter>

    @Operation(
        summary = "모델 자동 중지 연장",
        description = "자동 중지를 지금부터 hours(1~4) 뒤로 미룹니다. 팀 관리자만 할 수 있고 스택의 AccessLevel 이 full 이어야 합니다. " +
            "이미 그보다 늦게 멈출 예정이면 줄이지 않고 그 시각을 돌려줍니다. 이미 멈춘 모델은 다시 뜨지 않으므로 MODEL_NOT_READY 입니다.",
    )
    @ApiErrorResponses(
        GpuNotSupportedException::class,
        GpuServingDeniedException::class,
        NotTeamAdminException::class,
        ClusterNotFoundException::class,
        ServedModelNotFoundException::class,
        ModelNotReadyException::class,
        GpuClusterApiFailedException::class,
        AwsRoleNotAssumableException::class,
    )
    fun extendServedModel(clusterId: UUID, name: String, userId: UUID, request: ExtendServedModelRequest): ResponseEntity<ExtendServedModelResponse>

    @Operation(
        summary = "모델 성능 측정 요청",
        description = "오븐에 측정을 요청하고 요청 값(request, m-<epoch 초>)을 돌려줍니다. 측정은 클러스터에서 따로 돌고, 끝나면 status.measureRequest 에 " +
            "이 값이 적힙니다. 팀 관리자만 할 수 있고 스택의 AccessLevel 이 full 이어야 합니다. " +
            "phase 가 Ready 일 때만 받습니다. 이미 측정 중이거나(Measuring) 뜨는 중, 멈춤, 지우는 중이면 MODEL_NOT_READY 입니다.",
    )
    @ApiErrorResponses(
        GpuNotSupportedException::class,
        GpuServingDeniedException::class,
        NotTeamAdminException::class,
        ClusterNotFoundException::class,
        ServedModelNotFoundException::class,
        ModelNotReadyException::class,
        GpuClusterApiFailedException::class,
        AwsRoleNotAssumableException::class,
    )
    fun measureServedModel(clusterId: UUID, name: String, userId: UUID): ResponseEntity<MeasureServedModelResponse>
}
