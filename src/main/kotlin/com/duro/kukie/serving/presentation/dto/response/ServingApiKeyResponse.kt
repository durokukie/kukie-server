package com.duro.kukie.serving.presentation.dto.response

import com.duro.kukie.serving.domain.ServingApiKey
import java.time.LocalDateTime
import java.util.UUID

data class ServingApiKeyResponse(
    val id: UUID, val accessId: UUID, val name: String, val prefix: String,
    val createdAt: LocalDateTime, val expiresAt: LocalDateTime, val revokedAt: LocalDateTime?,
) {
    companion object {
        fun of(key: ServingApiKey) = ServingApiKeyResponse(key.id, key.accessId, key.name, key.prefix, key.createdAt, key.expiresAt, key.revokedAt)
    }
}

class IssuedServingApiKeyResponse(val key: ServingApiKeyResponse, val token: String) {
    override fun toString(): String = "IssuedServingApiKeyResponse(key=$key, token=[REDACTED])"
}

data class ServingApiKeyPageResponse(
    val content: List<ServingApiKeyResponse>, val page: Int, val size: Int,
    val totalElements: Long, val totalPages: Int,
)
