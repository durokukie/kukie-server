package com.duro.kukie.cluster.presentation

import com.duro.kukie.cluster.exception.ClusterInUseException
import com.duro.kukie.cluster.exception.ClusterNameTakenException
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.cluster.exception.ClusterProviderMismatchException
import com.duro.kukie.cluster.exception.KubeconfigRejectedException
import com.duro.kukie.cluster.exception.NothingToUpdateException
import com.duro.kukie.cluster.presentation.dto.request.RegisterClusterRequest
import com.duro.kukie.cluster.presentation.dto.request.UpdateClusterRequest
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.NotTeamAdminException
import com.duro.kukie.team.exception.NotTeamMemberException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface ClusterControllerDocs {

    @Operation(
        summary = "클러스터 등록",
        description = "kubeconfig 를 검사해 context 하나를 등록합니다. 팀 관리자만 등록할 수 있습니다. " +
            "exec·auth-provider·파일 경로·사설 주소를 쓰는 kubeconfig 는 거부하며 사유를 message 로 돌려줍니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(
        KubeconfigRejectedException::class,
        NotTeamMemberException::class,
        NotTeamAdminException::class,
        ClusterNameTakenException::class,
    )
    fun registerCluster(userId: UUID, request: RegisterClusterRequest): ResponseEntity<ClusterResponse>

    @Operation(
        summary = "클러스터 목록 조회",
        description = "속한 팀의 클러스터를 등록 순으로 조회합니다. teamId 를 주면 그 팀 것만 봅니다.",
    )
    @ApiErrorResponses(NotTeamMemberException::class)
    fun getClusters(userId: UUID, teamId: UUID?): ResponseEntity<List<ClusterResponse>>

    @Operation(summary = "클러스터 조회", description = "클러스터 하나를 조회합니다. 자격증명은 응답에 없습니다.")
    @ApiErrorResponses(ClusterNotFoundException::class)
    fun getCluster(clusterId: UUID, userId: UUID): ResponseEntity<ClusterResponse>

    @Operation(
        summary = "클러스터 수정",
        description = "이름·기본 namespace·kubeconfig 를 바꿉니다. 팀 관리자만 할 수 있습니다. " +
            "kubeconfig 를 바꾸면 지문을 다시 계산하고 상태를 disconnected 로 돌립니다. " +
            "EKS 클러스터는 이름만 바꿀 수 있습니다.",
    )
    @ApiErrorResponses(
        NothingToUpdateException::class,
        ClusterProviderMismatchException::class,
        KubeconfigRejectedException::class,
        NotTeamAdminException::class,
        ClusterNotFoundException::class,
        ClusterNameTakenException::class,
    )
    fun updateCluster(clusterId: UUID, userId: UUID, request: UpdateClusterRequest): ResponseEntity<ClusterResponse>

    @Operation(
        summary = "클러스터 삭제",
        description = "클러스터와 자격증명을 삭제합니다. 팀 관리자만 할 수 있습니다. " +
            "이 클러스터를 쓰는 대화가 있으면 삭제할 수 없습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        NotTeamAdminException::class,
        ClusterNotFoundException::class,
        ClusterInUseException::class,
    )
    fun deleteCluster(clusterId: UUID, userId: UUID): ResponseEntity<Unit>
}
