package com.duro.kukie.chat.presentation

import com.duro.kukie.chat.application.InterruptOrphanedRunsService
import com.duro.kukie.chat.application.UpdateRunService
import com.duro.kukie.chat.presentation.dto.request.InterruptRunsRequest
import com.duro.kukie.chat.presentation.dto.request.UpdateRunRequest
import com.duro.kukie.chat.presentation.dto.response.InterruptOrphanedRunsResponse
import com.duro.kukie.chat.presentation.dto.response.UpdateRunResponse
import com.duro.kukie.global.security.InternalApi
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@InternalApi
@RestController
@RequestMapping("/internal/runs")
class InternalRunController(
    private val updateRunService: UpdateRunService,
    private val interruptOrphanedRunsService: InterruptOrphanedRunsService,
) : InternalRunControllerDocs {

    @PatchMapping("/{runId}")
    override fun updateRun(
        @PathVariable runId: UUID,
        @RequestBody @Valid request: UpdateRunRequest,
    ): ResponseEntity<UpdateRunResponse> {
        return ResponseEntity.ok(updateRunService(request.toCommand(runId)))
    }

    @PostMapping("/interrupt-orphaned")
    override fun interruptOrphanedRuns(
        @RequestBody @Valid request: InterruptRunsRequest,
    ): ResponseEntity<InterruptOrphanedRunsResponse> {
        return ResponseEntity.ok(interruptOrphanedRunsService(request))
    }
}
