package com.duro.kukie.chat.presentation

import com.duro.kukie.chat.application.ContextPurpose
import com.duro.kukie.chat.application.FindRunByRequestService
import com.duro.kukie.chat.application.GetChatContextService
import com.duro.kukie.chat.application.InterruptChatService
import com.duro.kukie.chat.application.StartRunService
import com.duro.kukie.chat.presentation.dto.request.InterruptRunsRequest
import com.duro.kukie.chat.presentation.dto.request.StartRunRequest
import com.duro.kukie.chat.presentation.dto.response.ChatContextResponse
import com.duro.kukie.chat.presentation.dto.response.InterruptRunsResponse
import com.duro.kukie.chat.presentation.dto.response.RunResponse
import com.duro.kukie.chat.presentation.dto.response.StartRunResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.global.security.InternalApi
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@InternalApi
@RestController
@RequestMapping("/internal/conversations")
class InternalChatController(
    private val getChatContextService: GetChatContextService,
    private val findRunByRequestService: FindRunByRequestService,
    private val startRunService: StartRunService,
    private val interruptChatService: InterruptChatService,
) : InternalChatControllerDocs {

    @Authenticated
    @GetMapping("/{conversationId}/context")
    override fun getChatContext(
        @PathVariable conversationId: UUID,
        @AuthUser userId: UUID,
        @RequestParam purpose: String,
    ): ResponseEntity<ChatContextResponse> {
        return ResponseEntity.ok(getChatContextService(conversationId, userId, ContextPurpose.from(purpose)))
    }

    @Authenticated
    @GetMapping("/{conversationId}/runs/by-request/{requestId}")
    override fun findRunByRequest(
        @PathVariable conversationId: UUID,
        @PathVariable requestId: String,
        @AuthUser userId: UUID,
    ): ResponseEntity<RunResponse> {
        return ResponseEntity.ok(findRunByRequestService(conversationId, userId, requestId))
    }

    @Authenticated
    @PostMapping("/{conversationId}/runs")
    override fun startRun(
        @PathVariable conversationId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: StartRunRequest,
    ): ResponseEntity<StartRunResponse> {
        val response = startRunService(request.toCommand(conversationId, userId))
        val status = if (response.created) HttpStatus.CREATED else HttpStatus.OK

        return ResponseEntity.status(status).body(response)
    }

    @PostMapping("/{conversationId}/interrupt")
    override fun interruptChat(
        @PathVariable conversationId: UUID,
        @RequestBody @Valid request: InterruptRunsRequest,
    ): ResponseEntity<InterruptRunsResponse> {
        return ResponseEntity.ok(interruptChatService(request.toCommand(conversationId)))
    }
}
