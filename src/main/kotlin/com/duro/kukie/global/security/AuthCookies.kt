package com.duro.kukie.global.security

import com.duro.kukie.global.config.properties.AuthCookieProperties
import com.duro.kukie.global.config.properties.JwtProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 토큰을 httpOnly 쿠키로 굽고, 지우고, 읽는 유일한 자리.
 *
 * 웹은 토큰을 보관할 곳이 브라우저뿐이고 화면 코드가 토큰을 못 봐야 하므로(HttpOnly) 서버가 쿠키로 준다.
 * 앱(Electron)은 JSON 응답의 토큰을 쓰므로 쿠키를 항상 같이 굽더라도 해가 없다 — 그래서 요청 종류를 가르지 않는다.
 *
 * - `SameSite=Lax`: 다른 사이트에서 오는 POST 에는 쿠키가 안 실린다 → CSRF 를 막는다. GET 으로 상태를 바꾸는 API 가 없어야 한다
 * - `Path=/`: 한 주소 아래 `/api/`(에이전트)·`/member/`(이 서버) 둘 다에 실려야 한다
 * - `Max-Age`: 토큰 만료와 같게 둔다. 브라우저가 만료된 쿠키를 알아서 버린다
 */
@Component
class AuthCookies(
    private val cookieProperties: AuthCookieProperties,
    private val jwtProperties: JwtProperties,
) {

    fun issue(response: HttpServletResponse, accessToken: String, refreshToken: String) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookieProperties.accessName, accessToken, jwtProperties.accessTokenExpiration).toString())
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookieProperties.refreshName, refreshToken, jwtProperties.refreshTokenExpiration).toString())
    }

    /** 브라우저가 지우게 한다 — 같은 이름·경로로 `Max-Age=0`. 속성이 다르면 브라우저는 다른 쿠키로 보고 안 지운다. */
    fun clear(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookieProperties.accessName, "", Duration.ZERO).toString())
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(cookieProperties.refreshName, "", Duration.ZERO).toString())
    }

    fun accessToken(request: HttpServletRequest): String? = read(request, cookieProperties.accessName)

    fun refreshToken(request: HttpServletRequest): String? = read(request, cookieProperties.refreshName)

    private fun read(request: HttpServletRequest, name: String): String? =
        request.cookies?.firstOrNull { it.name == name }?.value?.takeIf { it.isNotBlank() }

    private fun cookie(name: String, value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookieProperties.secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
}
