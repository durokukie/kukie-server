package com.duro.kukie.chat.presentation.dto.request

import com.duro.kukie.chat.application.port.`in`.CreateChatCommand
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatVisibility
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateChatRequest(
    val clusterId: UUID? = null,

    val teamId: UUID? = null,

    @field:Size(max = 63)
    val namespace: String? = null,

    @field:Size(max = 200)
    val title: String? = null,

    val shared: Boolean? = null,
) {
    fun toCommand(userId: UUID) = CreateChatCommand(
        userId = userId,
        clusterId = clusterId,
        teamId = teamId,
        namespace = namespace?.trim()?.ifEmpty { null },
        title = title?.trim()?.ifEmpty { null } ?: ChatSession.DEFAULT_TITLE,
        visibility = if (shared == true) ChatVisibility.SHARED else ChatVisibility.PRIVATE,
    )
}
