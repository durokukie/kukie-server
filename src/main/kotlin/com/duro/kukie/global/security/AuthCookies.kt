package com.duro.kukie.global.security

import com.duro.kukie.auth.exception.CrossSiteCookieException
import com.duro.kukie.global.config.properties.AuthCookieProperties
import com.duro.kukie.global.config.properties.JwtProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/** 토큰 쿠키를 굽고·지우고·읽는 유일한 자리. 읽기는 `Sec-Fetch-Site` 가 same-origin/none 일 때만 허용한다. */
@Component
class AuthCookies(
    private val cookieProperties: AuthCookieProperties,
    private val jwtProperties: JwtProperties,
) {

    fun issue(response: HttpServletResponse, accessToken: String, refreshToken: String) {
        response.addSetCookie(cookie(cookieProperties.accessCookieName, accessToken, jwtProperties.accessTokenExpiration))
        response.addSetCookie(cookie(cookieProperties.refreshCookieName, refreshToken, jwtProperties.refreshTokenExpiration))
        clearLegacy(response)
    }

    /** 브라우저가 지우게 한다 — 같은 이름·경로로 `Max-Age=0`. 속성이 다르면 브라우저는 다른 쿠키로 보고 안 지운다. */
    fun clear(response: HttpServletResponse) {
        response.addSetCookie(cookie(cookieProperties.accessCookieName, "", Duration.ZERO))
        response.addSetCookie(cookie(cookieProperties.refreshCookieName, "", Duration.ZERO))
        clearLegacy(response)
    }

    /** access 쿠키의 토큰. 없으면 null, 있는데 출처가 우리 페이지가 아니면 403. */
    fun accessToken(request: HttpServletRequest): String? = read(request, cookieProperties.accessCookieName)

    /** refresh 쿠키의 토큰. 규칙은 access 와 같다. */
    fun refreshToken(request: HttpServletRequest): String? = read(request, cookieProperties.refreshCookieName)

    /** `__Host-` 이전에 구운 접두사 없는 쿠키는 읽지 않으니 브라우저에 남지 않게 지운다. */
    private fun clearLegacy(response: HttpServletResponse) {
        if (!cookieProperties.secure) return
        response.addSetCookie(cookie(cookieProperties.accessName, "", Duration.ZERO))
        response.addSetCookie(cookie(cookieProperties.refreshName, "", Duration.ZERO))
    }

    // 접두사 없는 이름으로 물러나 읽으면 형제 서브도메인이 심은 쿠키를 받아들이게 된다
    private fun read(request: HttpServletRequest, name: String): String? {
        val value = request.cookies?.firstOrNull { it.name == name }?.value?.takeIf { it.isNotBlank() } ?: return null
        if (request.getHeader(SEC_FETCH_SITE)?.trim()?.lowercase() !in SAME_ORIGIN_VALUES) {
            throw CrossSiteCookieException()
        }
        return value
    }

    private fun HttpServletResponse.addSetCookie(cookie: ResponseCookie) {
        addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    companion object {
        private const val SEC_FETCH_SITE = "Sec-Fetch-Site"
        // same-origin 은 우리 페이지, none 은 주소창 직접 입력
        private val SAME_ORIGIN_VALUES = setOf("same-origin", "none")
    }

    // __Host- 쿠키는 Secure·Path=/·Domain 없음이어야 브라우저가 받는다 — domain() 을 붙이지 않는다
    private fun cookie(name: String, value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookieProperties.secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
}
