package com.duro.kukie.hosting.application

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.hosting.domain.HostingAgentLogin
import com.duro.kukie.hosting.domain.HostingAgentLoginRepository
import com.duro.kukie.hosting.exception.HostingAgentDeniedException
import com.duro.kukie.hosting.exception.HostingAgentExpiredException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PollHostingAgentTokenService(
    private val hostingAgentLoginRepository: HostingAgentLoginRepository,
    private val hostingLongPolls: HostingLongPolls,
    private val jwtTokenProvider: JwtTokenProvider,
    private val hostingProperties: HostingProperties,
) {

    /**
     * 허용되면 토큰을, 아직이면 null 을 준다. 허용을 기다리며 최대 agentPollWait 동안 붙잡는다(자리가 있을 때만). 토큰은 한 번만 준다:
     * 허용된 로그인을 발급됨으로 바꾸는 쪽만 받는다. 응답을 놓치면 다시 로그인한다.
     */
    operator fun invoke(pollToken: String): String? = hostingLongPolls.hold { canWait ->
        val hash = HostingAgentLogins.hash(pollToken)
        val deadline = Instant.now().plus(if (canWait) hostingProperties.agentPollWait else java.time.Duration.ZERO)
        while (true) {
            val login = hostingAgentLoginRepository.findByPollTokenHash(hash) ?: throw HostingAgentExpiredException()
            when (login.status) {
                HostingAgentLogin.Status.APPROVED -> {
                    val issued = login.copy(status = HostingAgentLogin.Status.ISSUED)
                    if (!hostingAgentLoginRepository.transition(hash, HostingAgentLogin.Status.APPROVED, issued, HostingAgentLogins.DECIDED_TTL)) {
                        throw HostingAgentExpiredException()
                    }
                    return@hold jwtTokenProvider.generateHostingAgentToken(login.userId ?: throw HostingAgentExpiredException())
                }
                HostingAgentLogin.Status.DENIED -> throw HostingAgentDeniedException()
                HostingAgentLogin.Status.NOT_ALLOWED -> throw HostingNotAllowedException()
                HostingAgentLogin.Status.ISSUED -> throw HostingAgentExpiredException()
                HostingAgentLogin.Status.PENDING -> if (!Instant.now().isBefore(deadline)) return@hold null
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 1000L
    }
}
