package com.duro.kukie.chat.presentation.dto.request

import com.duro.kukie.chat.application.port.`in`.RenameChatCommand
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class RenameChatRequest(
    val title: String,
) {
    /** @NotBlank 는 no-break space 를 공백으로 보지 않으므로 Kotlin trim() 결과를 검사한다. */
    @get:JsonIgnore
    @get:NotBlank
    @get:Size(max = 200)
    val trimmedTitle: String
        get() = title.trim()

    fun toCommand(conversationId: UUID, userId: UUID) = RenameChatCommand(
        conversationId = conversationId,
        userId = userId,
        title = trimmedTitle,
    )
}
