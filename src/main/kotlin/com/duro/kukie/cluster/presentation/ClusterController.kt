package com.duro.kukie.cluster.presentation

import com.duro.kukie.cluster.application.DeleteClusterService
import com.duro.kukie.cluster.application.GetClusterService
import com.duro.kukie.cluster.application.GetClusterSetupService
import com.duro.kukie.cluster.application.GetClustersService
import com.duro.kukie.cluster.application.RegisterClusterService
import com.duro.kukie.cluster.application.UpdateClusterService
import com.duro.kukie.cluster.presentation.dto.request.RegisterClusterRequest
import com.duro.kukie.cluster.presentation.dto.request.UpdateClusterRequest
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import com.duro.kukie.cluster.presentation.dto.response.ClusterSetupResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/clusters")
class ClusterController(
    private val registerClusterService: RegisterClusterService,
    private val getClustersService: GetClustersService,
    private val getClusterService: GetClusterService,
    private val getClusterSetupService: GetClusterSetupService,
    private val updateClusterService: UpdateClusterService,
    private val deleteClusterService: DeleteClusterService,
) : ClusterControllerDocs {

    @PostMapping
    override fun registerCluster(
        @AuthUser userId: UUID,
        @RequestBody @Valid request: RegisterClusterRequest,
    ): ResponseEntity<ClusterResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(registerClusterService(request.toCommand(userId)))
    }

    @GetMapping
    override fun getClusters(
        @AuthUser userId: UUID,
        @RequestParam(required = false) teamId: UUID?,
    ): ResponseEntity<List<ClusterResponse>> {
        return ResponseEntity.ok(getClustersService(userId, teamId))
    }

    @GetMapping("/{clusterId}")
    override fun getCluster(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<ClusterResponse> {
        return ResponseEntity.ok(getClusterService(clusterId, userId))
    }

    @GetMapping("/{clusterId}/setup")
    override fun getClusterSetup(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<ClusterSetupResponse> {
        return ResponseEntity.ok(getClusterSetupService(clusterId, userId))
    }

    @PatchMapping("/{clusterId}")
    override fun updateCluster(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: UpdateClusterRequest,
    ): ResponseEntity<ClusterResponse> {
        return ResponseEntity.ok(updateClusterService(request.toCommand(clusterId, userId)))
    }

    @DeleteMapping("/{clusterId}")
    override fun deleteCluster(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        deleteClusterService(clusterId, userId)

        return ResponseEntity.noContent().build()
    }
}
