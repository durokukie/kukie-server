package com.duro.kukie.cluster.presentation

import com.duro.kukie.cluster.application.ChatWithServedModelService
import com.duro.kukie.cluster.application.CreateServedModelService
import com.duro.kukie.cluster.application.DeleteServedModelService
import com.duro.kukie.cluster.application.ExtendServedModelService
import com.duro.kukie.cluster.application.GetGpuNodeGroupsService
import com.duro.kukie.cluster.application.MeasureServedModelService
import com.duro.kukie.cluster.application.UpdateGpuNodeGroupService
import com.duro.kukie.cluster.presentation.dto.request.CreateServedModelRequest
import com.duro.kukie.cluster.presentation.dto.request.ExtendServedModelRequest
import com.duro.kukie.cluster.presentation.dto.request.ModelChatRequest
import com.duro.kukie.cluster.presentation.dto.request.UpdateGpuNodeGroupRequest
import com.duro.kukie.cluster.presentation.dto.response.ExtendServedModelResponse
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupResponse
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupsResponse
import com.duro.kukie.cluster.presentation.dto.response.MeasureServedModelResponse
import com.duro.kukie.cluster.presentation.dto.response.ServedModelResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/clusters/{clusterId}/gpu")
class GpuClusterController(
    private val getGpuNodeGroupsService: GetGpuNodeGroupsService,
    private val updateGpuNodeGroupService: UpdateGpuNodeGroupService,
    private val createServedModelService: CreateServedModelService,
    private val deleteServedModelService: DeleteServedModelService,
    private val chatWithServedModelService: ChatWithServedModelService,
    private val extendServedModelService: ExtendServedModelService,
    private val measureServedModelService: MeasureServedModelService,
) : GpuClusterControllerDocs {

    @GetMapping("/node-groups")
    override fun getGpuNodeGroups(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<GpuNodeGroupsResponse> {
        return ResponseEntity.ok(getGpuNodeGroupsService(clusterId, userId))
    }

    @PutMapping("/node-groups/{name}")
    override fun updateGpuNodeGroup(
        @PathVariable clusterId: UUID,
        @PathVariable name: String,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: UpdateGpuNodeGroupRequest,
    ): ResponseEntity<GpuNodeGroupResponse> {
        return ResponseEntity.ok(updateGpuNodeGroupService(request.toCommand(clusterId, userId, name)))
    }

    @PostMapping("/served-models")
    override fun createServedModel(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: CreateServedModelRequest,
    ): ResponseEntity<ServedModelResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(createServedModelService(request.toCommand(clusterId, userId)))
    }

    @DeleteMapping("/served-models/{name}")
    override fun deleteServedModel(
        @PathVariable clusterId: UUID,
        @PathVariable name: String,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        deleteServedModelService(clusterId, userId, name)

        return ResponseEntity.noContent().build()
    }

    @PostMapping("/served-models/{name}/chat")
    override fun chatWithServedModel(
        @PathVariable clusterId: UUID,
        @PathVariable name: String,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: ModelChatRequest,
    ): ResponseEntity<SseEmitter> {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            // 프록시가 모았다가 한 번에 보내지 않게 한다
            .header("X-Accel-Buffering", "no")
            .body(chatWithServedModelService(request.toCommand(clusterId, userId, name)))
    }

    @PostMapping("/served-models/{name}/extend")
    override fun extendServedModel(
        @PathVariable clusterId: UUID,
        @PathVariable name: String,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: ExtendServedModelRequest,
    ): ResponseEntity<ExtendServedModelResponse> {
        return ResponseEntity.ok(extendServedModelService(request.toCommand(clusterId, userId, name)))
    }

    @PostMapping("/served-models/{name}/measure")
    override fun measureServedModel(
        @PathVariable clusterId: UUID,
        @PathVariable name: String,
        @AuthUser userId: UUID,
    ): ResponseEntity<MeasureServedModelResponse> {
        return ResponseEntity.ok(measureServedModelService(clusterId, userId, name))
    }
}
