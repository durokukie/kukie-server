package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.hosting.application.GetHostingHistoryService
import com.duro.kukie.hosting.application.GetHostingPastBuildLogService
import com.duro.kukie.hosting.presentation.dto.response.HostingHistoryResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/hosting/history")
class HostingHistoryController(
    private val getHostingHistoryService: GetHostingHistoryService,
    private val getHostingPastBuildLogService: GetHostingPastBuildLogService,
) : HostingHistoryControllerDocs {

    @GetMapping
    override fun getHistory(@AuthUser userId: UUID): ResponseEntity<HostingHistoryResponse> {
        return ResponseEntity.ok(getHostingHistoryService(userId))
    }

    @GetMapping("/{app}/{generation}/deployments/{deploymentId}/log", produces = [TEXT_UTF8])
    override fun getBuildLog(
        @AuthUser userId: UUID,
        @PathVariable app: String,
        @PathVariable generation: String,
        @PathVariable deploymentId: String,
    ): ResponseEntity<String> {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(TEXT_UTF8))
            .body(getHostingPastBuildLogService(userId, app, generation, deploymentId))
    }

    companion object {
        private const val TEXT_UTF8 = "text/plain;charset=UTF-8"
    }
}
