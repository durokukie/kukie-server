package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** 웹 토큰 쿠키 설정. 실제 이름(`accessCookieName`)은 agent(`auth.py`)도 읽으므로 바꾸면 함께 바꾼다. */
@ConfigurationProperties(prefix = "auth.cookie")
data class AuthCookieProperties(
    val accessName: String = "kukie_access",
    val refreshName: String = "kukie_refresh",
    val secure: Boolean = true,
) {
    /** 브라우저에 굽고 읽는 이름. https 면 `__Host-` 를 붙여 형제 서브도메인이 `Domain=` 쿠키로 덮어쓰지 못하게 한다. */
    val accessCookieName: String get() = cookieName(accessName)

    val refreshCookieName: String get() = cookieName(refreshName)

    // 브라우저는 Secure 가 없는 __Host- 쿠키를 거부하므로 로컬 http 에서는 붙이지 않는다
    private fun cookieName(name: String): String = if (secure) "$HOST_PREFIX$name" else name

    companion object {
        const val HOST_PREFIX = "__Host-"
    }
}
