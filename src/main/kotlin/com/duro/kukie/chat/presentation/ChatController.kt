package com.duro.kukie.chat.presentation

import com.duro.kukie.chat.application.CreateChatService
import com.duro.kukie.chat.application.DeleteChatService
import com.duro.kukie.chat.application.GetChatService
import com.duro.kukie.chat.application.GetChatsService
import com.duro.kukie.chat.presentation.dto.request.CreateChatRequest
import com.duro.kukie.chat.presentation.dto.response.ChatDetailResponse
import com.duro.kukie.chat.presentation.dto.response.ChatResponse
import com.duro.kukie.chat.presentation.dto.response.CreateChatResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/conversations")
class ChatController(
    private val createChatService: CreateChatService,
    private val getChatsService: GetChatsService,
    private val getChatService: GetChatService,
    private val deleteChatService: DeleteChatService,
) : ChatControllerDocs {

    @PostMapping
    override fun createChat(
        @AuthUser userId: UUID,
        @RequestBody @Valid request: CreateChatRequest,
    ): ResponseEntity<CreateChatResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(createChatService(request.toCommand(userId)))
    }

    @GetMapping
    override fun getChats(
        @AuthUser userId: UUID,
        @RequestParam(required = false) clusterId: UUID?,
    ): ResponseEntity<List<ChatResponse>> {
        return ResponseEntity.ok(getChatsService(userId, clusterId))
    }

    @GetMapping("/{conversationId}")
    override fun getChat(
        @PathVariable conversationId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<ChatDetailResponse> {
        return ResponseEntity.ok(getChatService(conversationId, userId))
    }

    @DeleteMapping("/{conversationId}")
    override fun deleteChat(
        @PathVariable conversationId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        deleteChatService(conversationId, userId)

        return ResponseEntity.noContent().build()
    }
}
