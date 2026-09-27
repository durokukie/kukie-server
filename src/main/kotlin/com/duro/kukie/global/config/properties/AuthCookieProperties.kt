package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 웹 클라이언트가 토큰을 받아 두는 쿠키. 앱(Electron)은 JSON 응답의 토큰을 쓰고 이 쿠키는 무시한다.
 *
 * 이름은 agent 도 같은 값을 읽으므로(agent `auth.py`) 바꾸면 그쪽도 같이 바꾼다.
 * `secure` 는 https 에서만 쿠키가 실리게 하는 속성이라 로컬 http 개발에서는 꺼야 한다 (`AUTH_COOKIE_SECURE=false`).
 */
@ConfigurationProperties(prefix = "auth.cookie")
data class AuthCookieProperties(
    val accessName: String = "kukie_access",
    val refreshName: String = "kukie_refresh",
    val secure: Boolean = true,
)
