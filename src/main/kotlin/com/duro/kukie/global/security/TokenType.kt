package com.duro.kukie.global.security

enum class TokenType(val value: String) {
    ACCESS("access"),
    REFRESH("refresh"),

    /** 코딩 에이전트가 호스팅에 배포할 때 쓰는 토큰. @HostingAgentToken 핸들러에서만 받는다. */
    HOSTING_AGENT("hosting_agent"),
}
