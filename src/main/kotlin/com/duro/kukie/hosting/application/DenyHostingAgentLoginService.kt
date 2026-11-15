package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingAgentLogin
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DenyHostingAgentLoginService(
    private val hostingAgentLogins: HostingAgentLogins,
) {

    operator fun invoke(userId: UUID, code: String) {
        val (hash, login) = hostingAgentLogins.pending(userId, code)
        hostingAgentLogins.decide(hash, login.copy(status = HostingAgentLogin.Status.DENIED))
    }
}
