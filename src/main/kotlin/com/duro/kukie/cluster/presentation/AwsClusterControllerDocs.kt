package com.duro.kukie.cluster.presentation

import com.duro.kukie.cluster.exception.AwsConnectionNotStartedException
import com.duro.kukie.cluster.exception.AwsRegionNotSupportedException
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.cluster.exception.ClusterNameTakenException
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.exception.EksAccessEntryDisabledException
import com.duro.kukie.cluster.exception.EksClusterNotFoundException
import com.duro.kukie.cluster.exception.EksClusterNotReadyException
import com.duro.kukie.cluster.exception.EksEndpointRejectedException
import com.duro.kukie.cluster.exception.InvalidNamespaceException
import com.duro.kukie.cluster.presentation.dto.request.GetEksCandidatesRequest
import com.duro.kukie.cluster.presentation.dto.request.RegisterEksClusterRequest
import com.duro.kukie.cluster.presentation.dto.request.StartAwsConnectionRequest
import com.duro.kukie.cluster.presentation.dto.response.AwsConnectionResponse
import com.duro.kukie.cluster.presentation.dto.response.AwsConnectionStatusResponse
import com.duro.kukie.cluster.presentation.dto.response.AwsDisconnectionResponse
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import com.duro.kukie.cluster.presentation.dto.response.EksClusterCandidateResponse
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.NotTeamAdminException
import com.duro.kukie.team.exception.NotTeamMemberException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface AwsClusterControllerDocs {

    @Operation(
        summary = "AWS 연결 시작",
        description = "팀의 ExternalId 를 발급하고(이미 있으면 그대로) 고객 계정에 KukieAccess-<ExternalId> 역할을 만드는 " +
            "CloudFormation 빠른 생성 링크를 돌려줍니다. 팀 관리자만 할 수 있습니다.",
    )
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class)
    fun startAwsConnection(userId: UUID, request: StartAwsConnectionRequest): ResponseEntity<AwsConnectionResponse>

    @Operation(
        summary = "AWS 연결 상태",
        description = "템플릿 v2 스택이 만들어지면 서버가 그 알림을 확인해 accountId 를 채웁니다. 화면은 스택을 만드는 동안 " +
            "이 값이 생길 때까지 물어봅니다. 연결되어 있으면 그 스택을 여는 콘솔 주소(stackConsoleUrl)와 스택 이름(stackName)도 줍니다. " +
            "연결을 해제한 뒤에는 해제 전 스택의 주소·이름과, 그 역할을 맡아 보아 고객이 스택을 지웠는지(stackDeleted)를 줍니다. " +
            "지워진 것을 확인한 뒤에는 다시 연결할 때까지 true 이고, 확인하지 못하면 null 입니다. 팀 관리자만 할 수 있습니다.",
    )
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class, AwsConnectionNotStartedException::class)
    fun getAwsConnection(userId: UUID, teamId: UUID): ResponseEntity<AwsConnectionStatusResponse>

    @Operation(
        summary = "AWS 연결 해제",
        description = "팀이 등록한 EKS 마다 Kukie 의 access entry 를 지우고(실패해도 해제는 계속합니다) 연결 상태를 비웁니다. " +
            "Kukie 는 고객 스택을 지울 수 없으므로 고객이 스택을 지울 CloudFormation 콘솔 주소를 돌려줍니다. 등록한 클러스터와 " +
            "ExternalId 는 남아, 같은 스택 이름으로 다시 연결하면 access entry 를 다시 붙입니다. 팀 관리자만 할 수 있습니다.",
    )
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class, AwsConnectionNotStartedException::class)
    fun disconnectAwsConnection(userId: UUID, teamId: UUID): ResponseEntity<AwsDisconnectionResponse>

    @Operation(
        summary = "EKS 클러스터 후보 조회",
        description = "고객 계정의 KukieAccess 역할을 맡아 설정된 리전의 EKS 클러스터를 나열합니다. 팀 관리자만 할 수 있습니다.",
    )
    @ApiErrorResponses(
        NotTeamMemberException::class,
        NotTeamAdminException::class,
        AwsConnectionNotStartedException::class,
        AwsRoleNotAssumableException::class,
        EksAccessDeniedException::class,
    )
    fun getEksCandidates(userId: UUID, request: GetEksCandidatesRequest): ResponseEntity<List<EksClusterCandidateResponse>>

    @Operation(
        summary = "EKS 클러스터 등록",
        description = "KukieAccess 역할을 클러스터의 access entry 로 넣고 고른 namespace 에 AmazonEKSEditPolicy 를 붙인 뒤 " +
            "클러스터를 등록합니다. 자격증명은 저장하지 않고 연결할 때마다 토큰을 새로 만듭니다. 팀 관리자만 할 수 있습니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(
        NotTeamMemberException::class,
        NotTeamAdminException::class,
        AwsRegionNotSupportedException::class,
        InvalidNamespaceException::class,
        AwsConnectionNotStartedException::class,
        AwsRoleNotAssumableException::class,
        EksClusterNotFoundException::class,
        EksClusterNotReadyException::class,
        EksAccessEntryDisabledException::class,
        EksEndpointRejectedException::class,
        EksAccessDeniedException::class,
        ClusterNameTakenException::class,
    )
    fun registerEksCluster(userId: UUID, request: RegisterEksClusterRequest): ResponseEntity<ClusterResponse>
}
