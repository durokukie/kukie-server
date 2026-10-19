package com.duro.kukie.cluster.presentation

import com.duro.kukie.cluster.application.DisconnectAwsConnectionService
import com.duro.kukie.cluster.application.GetAwsConnectionService
import com.duro.kukie.cluster.application.GetEksCandidatesService
import com.duro.kukie.cluster.application.RegisterEksClusterService
import com.duro.kukie.cluster.application.StartAwsConnectionService
import com.duro.kukie.cluster.presentation.dto.request.GetEksCandidatesRequest
import com.duro.kukie.cluster.presentation.dto.request.RegisterEksClusterRequest
import com.duro.kukie.cluster.presentation.dto.request.StartAwsConnectionRequest
import com.duro.kukie.cluster.presentation.dto.response.AwsConnectionResponse
import com.duro.kukie.cluster.presentation.dto.response.AwsConnectionStatusResponse
import com.duro.kukie.cluster.presentation.dto.response.AwsDisconnectionResponse
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import com.duro.kukie.cluster.presentation.dto.response.EksClusterCandidateResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/clusters/aws")
class AwsClusterController(
    private val startAwsConnectionService: StartAwsConnectionService,
    private val getAwsConnectionService: GetAwsConnectionService,
    private val disconnectAwsConnectionService: DisconnectAwsConnectionService,
    private val getEksCandidatesService: GetEksCandidatesService,
    private val registerEksClusterService: RegisterEksClusterService,
) : AwsClusterControllerDocs {

    @PostMapping("/connection")
    override fun startAwsConnection(
        @AuthUser userId: UUID,
        @RequestBody @Valid request: StartAwsConnectionRequest,
    ): ResponseEntity<AwsConnectionResponse> {
        return ResponseEntity.ok(startAwsConnectionService(request.toCommand(userId)))
    }

    @GetMapping("/connection")
    override fun getAwsConnection(
        @AuthUser userId: UUID,
        @RequestParam teamId: UUID,
    ): ResponseEntity<AwsConnectionStatusResponse> {
        return ResponseEntity.ok(getAwsConnectionService(teamId, userId))
    }

    @DeleteMapping("/connection")
    override fun disconnectAwsConnection(
        @AuthUser userId: UUID,
        @RequestParam teamId: UUID,
    ): ResponseEntity<AwsDisconnectionResponse> {
        return ResponseEntity.ok(disconnectAwsConnectionService(teamId, userId))
    }

    @PostMapping("/candidates")
    override fun getEksCandidates(
        @AuthUser userId: UUID,
        @RequestBody @Valid request: GetEksCandidatesRequest,
    ): ResponseEntity<List<EksClusterCandidateResponse>> {
        return ResponseEntity.ok(getEksCandidatesService(request.toCommand(userId)))
    }

    @PostMapping
    override fun registerEksCluster(
        @AuthUser userId: UUID,
        @RequestBody @Valid request: RegisterEksClusterRequest,
    ): ResponseEntity<ClusterResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(registerEksClusterService(request.toCommand(userId)))
    }
}
