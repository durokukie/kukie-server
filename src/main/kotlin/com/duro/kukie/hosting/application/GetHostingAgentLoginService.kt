package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.presentation.dto.response.HostingAgentLoginRequestResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetHostingAgentLoginService(
    private val hostingAgentLogins: HostingAgentLogins,
) {

    /** 허용 화면이 보여 줄 요청. 지금 자기가 시작한 요청인지 가릴 수 있게 요청 시각을 준다. */
    operator fun invoke(userId: UUID, code: String): HostingAgentLoginRequestResponse {
        val (_, login) = hostingAgentLogins.pending(userId, code)
        return HostingAgentLoginRequestResponse(HostingAgentLogins.display(login.code), login.requestedAt)
    }
}
