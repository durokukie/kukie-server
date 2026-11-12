package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.hosting.application.GetHostingAppService
import com.duro.kukie.hosting.application.GetHostingAppsService
import com.duro.kukie.hosting.application.GetHostingBuildLogService
import com.duro.kukie.hosting.application.SendHostingCommandService
import com.duro.kukie.hosting.application.StartHostingAppService
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.presentation.dto.request.StartHostingAppRequest
import com.duro.kukie.hosting.presentation.dto.response.HostingAppDetailResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAppsResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/hosting/apps")
class HostingController(
    private val getHostingAppsService: GetHostingAppsService,
    private val getHostingAppService: GetHostingAppService,
    private val getHostingBuildLogService: GetHostingBuildLogService,
    private val sendHostingCommandService: SendHostingCommandService,
    private val startHostingAppService: StartHostingAppService,
) : HostingControllerDocs {

    @GetMapping
    override fun getApps(@AuthUser userId: UUID): ResponseEntity<HostingAppsResponse> {
        return ResponseEntity.ok(getHostingAppsService(userId))
    }

    @PostMapping
    override fun start(@AuthUser userId: UUID, @RequestBody @Valid request: StartHostingAppRequest): ResponseEntity<HostingCommandResponse> {
        return ResponseEntity.accepted().body(startHostingAppService(userId, request.repository))
    }

    @GetMapping("/{app}")
    override fun getApp(@AuthUser userId: UUID, @PathVariable app: String): ResponseEntity<HostingAppDetailResponse> {
        return ResponseEntity.ok(getHostingAppService(userId, app))
    }

    @GetMapping("/{app}/deployments/{deploymentId}/log", produces = [TEXT_UTF8])
    override fun getBuildLog(
        @AuthUser userId: UUID,
        @PathVariable app: String,
        @PathVariable deploymentId: String,
    ): ResponseEntity<String> {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(TEXT_UTF8)).body(getHostingBuildLogService(userId, app, deploymentId))
    }

    @PostMapping("/{app}/redeploy")
    override fun redeploy(@AuthUser userId: UUID, @PathVariable app: String): ResponseEntity<HostingCommandResponse> {
        return ResponseEntity.accepted().body(sendHostingCommandService(userId, app, HostingCommand.REDEPLOY))
    }

    @DeleteMapping("/{app}")
    override fun delete(@AuthUser userId: UUID, @PathVariable app: String): ResponseEntity<HostingCommandResponse> {
        return ResponseEntity.accepted().body(sendHostingCommandService(userId, app, HostingCommand.DELETE))
    }

    companion object {
        private const val TEXT_UTF8 = "text/plain;charset=UTF-8"
    }
}
