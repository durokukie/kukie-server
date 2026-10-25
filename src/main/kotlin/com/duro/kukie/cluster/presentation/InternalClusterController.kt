package com.duro.kukie.cluster.presentation

import com.duro.kukie.cluster.application.GetClusterConnectionService
import com.duro.kukie.cluster.application.UpdateClusterGpuService
import com.duro.kukie.cluster.application.UpdateClusterStatusService
import com.duro.kukie.cluster.presentation.dto.request.UpdateClusterGpuRequest
import com.duro.kukie.cluster.presentation.dto.request.UpdateClusterStatusRequest
import com.duro.kukie.cluster.presentation.dto.response.ClusterConnectionResponse
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.global.security.InternalApi
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@InternalApi
@RestController
@RequestMapping("/internal/clusters")
class InternalClusterController(
    private val getClusterConnectionService: GetClusterConnectionService,
    private val updateClusterStatusService: UpdateClusterStatusService,
    private val updateClusterGpuService: UpdateClusterGpuService,
) : InternalClusterControllerDocs {

    @Authenticated
    @GetMapping("/{clusterId}/connection")
    override fun getClusterConnection(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
        @RequestParam(required = false) expectedFingerprint: String?,
    ): ResponseEntity<ClusterConnectionResponse> {
        return ResponseEntity.ok(getClusterConnectionService(clusterId, userId, expectedFingerprint))
    }

    @PatchMapping("/{clusterId}/status")
    override fun updateClusterStatus(
        @PathVariable clusterId: UUID,
        @RequestBody @Valid request: UpdateClusterStatusRequest,
    ): ResponseEntity<ClusterResponse> {
        return ResponseEntity.ok(updateClusterStatusService(request.toCommand(clusterId)))
    }

    @PatchMapping("/{clusterId}/gpu")
    override fun updateClusterGpu(
        @PathVariable clusterId: UUID,
        @RequestBody @Valid request: UpdateClusterGpuRequest,
    ): ResponseEntity<ClusterResponse> {
        return ResponseEntity.ok(updateClusterGpuService(request.toCommand(clusterId)))
    }
}
