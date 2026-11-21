package com.duro.kukie.hosting.presentation.dto.response

import com.duro.kukie.hosting.domain.HostingAgentKey
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** 연결한 에이전트. lastUsedAt 은 1분 단위로 갱신된다. */
data class HostingAgentKeyResponse(
    val id: UUID,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val expiresAt: Instant,
) {
    companion object {
        fun of(key: HostingAgentKey): HostingAgentKeyResponse {
            val lastUsedAt = key.lastUsedAt.toInstant(ZoneOffset.UTC)
            // 감사 시각(createdAt)은 서버 기본 시간대로 적힌다
            return HostingAgentKeyResponse(key.id, key.createdAt.atZone(ZoneId.systemDefault()).toInstant(), lastUsedAt, lastUsedAt.plus(HostingAgentKey.UNUSED_LIMIT))
        }
    }
}
