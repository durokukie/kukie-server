package com.duro.kukie.hosting.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 코딩 에이전트의 로그인 요청(device code 방식). 에이전트가 사용자에게 code 를 보여 주고, 사용자가 웹에서 code 를 입력해 허용하면
 * 에이전트가 pollToken 으로 토큰을 한 번 받는다. userId 는 허용한 회원.
 */
data class HostingAgentLogin(
    val code: String,
    val status: Status,
    val requestedAt: Instant,
    val userId: UUID? = null,
) {
    enum class Status { PENDING, APPROVED, DENIED, NOT_ALLOWED, ISSUED }
}

/** pollToken 은 해시로만 둔다. 상태 바꾸기는 모두 "그 상태일 때만"으로 원자적이다. */
interface HostingAgentLoginRepository {

    /** 코드가 이미 쓰이고 있으면 false. */
    fun create(pollTokenHash: String, login: HostingAgentLogin, ttl: Duration): Boolean

    fun findByPollTokenHash(pollTokenHash: String): HostingAgentLogin?

    fun findPollTokenHashByCode(code: String): String?

    /** 지금 상태가 from 일 때만 바꾸고 코드 키와 함께 ttl 을 다시 건다. 바꿨으면 true. */
    fun transition(pollTokenHash: String, from: HostingAgentLogin.Status, to: HostingAgentLogin, ttl: Duration): Boolean
}
