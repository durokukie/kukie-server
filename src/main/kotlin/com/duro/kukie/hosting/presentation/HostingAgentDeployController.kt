package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.global.security.HostingAgentToken
import com.duro.kukie.hosting.application.CreateHostingAgentDeployService
import com.duro.kukie.hosting.application.StartHostingAgentDeployService
import com.duro.kukie.hosting.application.WaitHostingAgentDeployService
import com.duro.kukie.hosting.presentation.dto.request.CreateHostingAgentDeployRequest
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentDeployResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentDeployStatusResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 코딩 에이전트가 폴더를 올려 배포하고 결과를 받는다. 에이전트 토큰으로 부른다. 본문은 폼이다. */
@Authenticated
@HostingAgentToken
@RestController
@RequestMapping("/hosting/agent/deploys")
class HostingAgentDeployController(
    private val createHostingAgentDeployService: CreateHostingAgentDeployService,
    private val startHostingAgentDeployService: StartHostingAgentDeployService,
    private val waitHostingAgentDeployService: WaitHostingAgentDeployService,
) : HostingAgentDeployControllerDocs {

    @PostMapping
    override fun create(@AuthUser userId: UUID, @Valid @ModelAttribute request: CreateHostingAgentDeployRequest): ResponseEntity<HostingAgentDeployResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(createHostingAgentDeployService(request.toCommand(userId)))
    }

    @PostMapping("/{deployId}/start")
    override fun start(@AuthUser userId: UUID, @PathVariable deployId: String): ResponseEntity<HostingCommandResponse> {
        return ResponseEntity.accepted().body(startHostingAgentDeployService(userId, deployId))
    }

    @GetMapping("/{deployId}/wait")
    override fun wait(
        @AuthUser userId: UUID,
        @PathVariable deployId: String,
        @RequestParam(required = false) timeout: Long?,
    ): ResponseEntity<HostingAgentDeployStatusResponse> {
        return ResponseEntity.ok(waitHostingAgentDeployService(userId, deployId, timeout))
    }
}
