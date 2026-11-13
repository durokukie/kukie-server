package com.duro.kukie.hosting.presentation.dto.response

import java.time.Instant

data class HostingAgentLoginResponse(
    /** 사용자가 허용 화면에 입력할 코드. 에이전트가 사용자에게 보여 준다. */
    val code: String,
    /** 허용 화면 주소({WEB_APP_URL}/?go=agent). 코드는 들어 있지 않다. */
    val loginUrl: String,
    /** 에이전트가 토큰을 받을 때 쓰는 값. 사용자에게 보여 주지 않는다. */
    val pollToken: String,
    val expiresIn: Long,
)

data class HostingAgentLoginRequestResponse(
    val code: String,
    /** 에이전트가 로그인을 시작한 시각. 지금 요청한 것이 아니면 남에게 받은 코드일 수 있다. */
    val requestedAt: Instant,
)

data class HostingAgentPendingResponse(val status: String = "pending")
