package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingAgentLogin
import com.duro.kukie.hosting.exception.HostingGitHubNotLinkedException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ApproveHostingAgentLoginService(
    private val hostingAgentLogins: HostingAgentLogins,
    private val hostingApps: HostingApps,
) {

    /** GitHub 계정을 연결했고 배포기가 받아 주는 계정일 때만 허용한다. 받아 주지 않으면 에이전트도 그렇게 알아 기다리지 않는다. */
    operator fun invoke(userId: UUID, code: String) {
        val (hash, login) = hostingAgentLogins.pending(userId, code)
        val account = hostingApps.accountOf(userId) ?: throw HostingGitHubNotLinkedException()
        if (!hostingApps.allowed(account)) {
            hostingAgentLogins.decide(hash, login.copy(status = HostingAgentLogin.Status.NOT_ALLOWED))
            throw HostingNotAllowedException()
        }
        hostingAgentLogins.decide(hash, login.copy(status = HostingAgentLogin.Status.APPROVED, userId = userId))
    }
}
