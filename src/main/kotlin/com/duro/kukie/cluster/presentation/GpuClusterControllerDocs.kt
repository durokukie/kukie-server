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
import com.duro.kukie.cluster.exception.NodeGroupBusyException
import com.duro.kukie.cluster.exception.ServedModelExistsException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import com.duro.kukie.cluster.presentation.dto.request.CreateServedModelRequest
import com.duro.kukie.cluster.presentation.dto.request.UpdateGpuNodeGroupRequest
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupResponse
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupsResponse
import com.duro.kukie.cluster.presentation.dto.response.ServedModelResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.NotTeamAdminException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface GpuClusterControllerDocs {

    @Operation(
        summary = "GPU 노드 그룹 조회",
        description = "EKS 클러스터의 GPU 노드 그룹(인스턴스 종류가 g·p 뒤에 숫자)을 조회합니다. EKS 가 아니면 supported 가 false 이고 목록은 비어 있습니다. " +
            "control 은 스택의 AccessLevel 이 full 이고 GpuNodeControl 이 true 일 때만 true 입니다. " +
            "고객 역할이 템플릿 v3 이전이라 노드 그룹을 읽지 못하면 EKS_ACCESS_DENIED 입니다.",
    )
    @ApiErrorResponses(
        ClusterNotFoundException::class,
        AwsRoleNotAssumableException::class,
        EksClusterNotFoundException::class,
        EksAccessDeniedException::class,
    )
    fun getGpuNodeGroups(clusterId: UUID, userId: UUID): ResponseEntity<GpuNodeGroupsResponse>

    @Operation(
        summary = "GPU 노드 수 변경",
        description = "GPU 노드 그룹의 desired 만 바꿉니다. 팀 관리자만 할 수 있고 min·max 는 바꾸지 않습니다. " +
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
            "GPU 한 장에 모델 하나이므로 그 namespace 에 ServedModel 이 이미 있으면 SERVED_MODEL_EXISTS 입니다.",
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
        description = "llm namespace 의 ServedModel 을 지웁니다. 팀 관리자만 할 수 있고 스택의 AccessLevel 이 full 이어야 합니다. 내려받은 모델 파일은 남습니다.",
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
}
