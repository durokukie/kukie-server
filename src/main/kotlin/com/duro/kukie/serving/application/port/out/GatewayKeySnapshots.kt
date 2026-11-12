package com.duro.kukie.serving.application.port.out

import java.time.LocalDateTime
import java.util.UUID

data class UsableKeyRow(val keyId: UUID, val tokenHash: String, val clusterId: UUID, val expiresAt: LocalDateTime)

interface GatewayKeySnapshots {
    /** 지금 쓸 수 있는 키(폐기 X, 만료 전, access ACTIVE + 사용자 있음)를 최대 [limit]개 읽는다. */
    fun usable(now: LocalDateTime, limit: Int): List<UsableKeyRow>
}
