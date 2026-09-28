package com.duro.kukie.auth.domain

import java.time.Duration
import java.util.UUID

/** 앱 넘겨주기 코드 한 장 — 누구의 세션인지와, 앱이 시작할 때 맡긴 PKCE 검증값. */
data class HandoffTicket(
    val userId: UUID,
    val codeChallenge: String,
)

/** 앱 넘겨주기 1회용 코드 저장소. */
interface HandoffCodeRepository {
    fun save(code: String, ticket: HandoffTicket, ttl: Duration)

    /** 꺼내면서 원자적으로 지운다. 같은 코드로 두 번째 부르면 null. */
    fun take(code: String): HandoffTicket?
}
