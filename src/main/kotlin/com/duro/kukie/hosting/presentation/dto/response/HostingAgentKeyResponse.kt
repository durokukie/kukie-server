package com.duro.kukie.hosting.presentation.dto.response

import com.duro.kukie.hosting.domain.HostingAgentKey
import java.time.LocalDateTime
import java.util.UUID

/** 연결한 에이전트. 시각은 UTC 이고, lastUsedAt 은 한 시간 단위로만 갱신된다. */
data class HostingAgentKeyResponse(
    val id: UUID,
    val createdAt: LocalDateTime,
    val lastUsedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
) {
    companion object {
        fun of(key: HostingAgentKey) = HostingAgentKeyResponse(key.id, key.createdAt, key.lastUsedAt, key.lastUsedAt.plus(HostingAgentKey.UNUSED_LIMIT))
    }
}
