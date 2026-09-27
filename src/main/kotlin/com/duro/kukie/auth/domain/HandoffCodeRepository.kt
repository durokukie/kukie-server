package com.duro.kukie.auth.domain

import java.time.Duration
import java.util.UUID

/** 앱 넘겨주기 코드 한 장 — 누구의 세션인지와, 앱이 시작할 때 맡긴 PKCE 검증값. */
data class HandoffTicket(
    val userId: UUID,
    val codeChallenge: String,
)

/**
 * 앱 넘겨주기 코드 저장소 (DURO-109). 코드는 1회용이고 짧게 산다.
 *
 * 시스템 브라우저에서 로그인을 마친 웹 페이지가 코드를 받아 `kukie://auth?code=…` 로 앱에 건네고, 앱이 그 코드를
 * 쿠키로 바꾼다. 브라우저 → OS → 앱으로 지나는 짧은 길이라 오래 살 이유가 없다.
 */
interface HandoffCodeRepository {
    fun save(code: String, ticket: HandoffTicket, ttl: Duration)

    /** 있으면 돌려주고 **동시에 지운다** — 같은 코드를 두 번 내면 두 번째는 null. 원자적이어야 한다. */
    fun take(code: String): HandoffTicket?
}
