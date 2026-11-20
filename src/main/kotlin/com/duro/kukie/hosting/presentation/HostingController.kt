package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.hosting.application.AnalyzeHostingFailureService
import com.duro.kukie.hosting.application.ConnectHostingAppService
import com.duro.kukie.hosting.application.ExportHostingAppRecordService
import com.duro.kukie.hosting.application.GetHostingRuntimeLogService
import com.duro.kukie.hosting.application.GetHostingAppService
import com.duro.kukie.hosting.application.GetHostingAppsService
import com.duro.kukie.hosting.application.GetHostingBuildLogService
import com.duro.kukie.hosting.application.SendHostingCommandService
import com.duro.kukie.hosting.application.StartHostingAppService
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.presentation.dto.request.ConnectHostingAppRequest
import com.duro.kukie.hosting.presentation.dto.request.StartHostingAppRequest
import com.duro.kukie.hosting.presentation.dto.response.HostingAppDetailResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAppRecordResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAppsResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingFailureAnalysisResponse
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
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
    private val connectHostingAppService: ConnectHostingAppService,
    private val analyzeHostingFailureService: AnalyzeHostingFailureService,
    private val getHostingRuntimeLogService: GetHostingRuntimeLogService,
    private val exportHostingAppRecordService: ExportHostingAppRecordService,
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

    @GetMapping("/{app}/logs", produces = [TEXT_UTF8])
    override fun getRuntimeLog(
        @AuthUser userId: UUID,
        @PathVariable app: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
    ): ResponseEntity<String> {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(TEXT_UTF8)).body(getHostingRuntimeLogService(userId, app, from, to))
    }

    @GetMapping("/{app}/record")
    override fun exportRecord(@AuthUser userId: UUID, @PathVariable app: String): ResponseEntity<HostingAppRecordResponse> {
        val record = exportHostingAppRecordService(userId, app)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("kukie-app-${record.app.app}.json").build().toString())
            .body(record)
    }

    @PostMapping("/{app}/deployments/{deploymentId}/analysis")
    override fun analyzeFailure(
        @AuthUser userId: UUID,
        @PathVariable app: String,
        @PathVariable deploymentId: String,
    ): ResponseEntity<HostingFailureAnalysisResponse> {
        return ResponseEntity.ok(analyzeHostingFailureService(userId, app, deploymentId))
    }

    @PostMapping("/{app}/connect")
    override fun connect(
        @AuthUser userId: UUID,
        @PathVariable app: String,
        @RequestBody @Valid request: ConnectHostingAppRequest,
    ): ResponseEntity<HostingCommandResponse> {
        return ResponseEntity.accepted().body(connectHostingAppService(request.toCommand(userId, app)))
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
