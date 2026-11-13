package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.hosting.domain.HostingAgentLogin
import com.duro.kukie.hosting.domain.HostingAgentLoginRepository
import com.duro.kukie.hosting.exception.HostingAgentExpiredException
import com.duro.kukie.hosting.exception.HostingTooManyRequestsException
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

/** 로그인 요청을 code 나 pollToken 으로 찾는다. 웹 허용 화면(code)과 에이전트(pollToken)가 함께 쓴다. */
@Component
class HostingAgentLogins(
    private val hostingAgentLoginRepository: HostingAgentLoginRepository,
    private val hostingAgentRateLimit: HostingAgentRateLimit,
) {

    /** 회원이 입력한 코드의 대기 중인 요청. 코드를 넣어 보며 맞히지 못하게 회원마다 1분에 20번까지 찾는다. */
    fun pending(userId: UUID, code: String): Pair<String, HostingAgentLogin> {
        if (!hostingAgentRateLimit.tryAcquire("code:$userId", CODE_LOOKUPS_PER_MINUTE, Duration.ofMinutes(1))) throw HostingTooManyRequestsException()
        val hash = hostingAgentLoginRepository.findPollTokenHashByCode(normalize(code)) ?: throw HostingAgentExpiredException()
        val login = hostingAgentLoginRepository.findByPollTokenHash(hash) ?: throw HostingAgentExpiredException()
        if (login.status != HostingAgentLogin.Status.PENDING) throw HostingAgentExpiredException()
        return hash to login
    }

    /** 대기 중일 때만 결정을 적는다. 그사이 다른 결정이 먼저 적혔으면 만료로 답한다. */
    fun decide(hash: String, login: HostingAgentLogin) {
        if (!hostingAgentLoginRepository.transition(hash, HostingAgentLogin.Status.PENDING, login, DECIDED_TTL)) throw HostingAgentExpiredException()
    }

    companion object {
        /** 사람이 읽고 옮겨 적기 쉽게 헷갈리는 글자(0·O·1·I)를 뺐다. */
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 8

        /** 허용·거부한 뒤 에이전트가 결과를 받을 시간. 에이전트가 코드를 보여 주고 사용자 답을 기다렸다 부를 수 있어 넉넉히 둔다 */
        val DECIDED_TTL: Duration = Duration.ofMinutes(10)
        private const val CODE_LOOKUPS_PER_MINUTE = 20

        /** 사용자가 입력한 코드: 대소문자와 하이픈·공백을 가리지 않는다. 화면에는 ABCD-EFGH 로 보인다. */
        fun normalize(code: String): String = code.uppercase().filter { it in CODE_ALPHABET }

        fun display(code: String): String = code.chunked(CODE_LENGTH / 2).joinToString("-")

        fun hash(pollToken: String): String =
            MessageDigest.getInstance("SHA-256").digest(pollToken.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
