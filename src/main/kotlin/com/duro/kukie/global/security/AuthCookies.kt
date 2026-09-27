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

/**
 * 토큰을 httpOnly 쿠키로 굽고, 지우고, 읽는 유일한 자리.
 *
 * 웹은 토큰을 보관할 곳이 브라우저뿐이고 화면 코드가 토큰을 못 봐야 하므로(HttpOnly) 서버가 쿠키로 준다.
 * 앱(Electron)은 JSON 응답의 토큰을 쓰므로 쿠키를 항상 같이 굽더라도 해가 없다 — 그래서 요청 종류를 가르지 않는다.
 *
 * - `SameSite=Lax`: 다른 사이트에서 오는 POST 에는 쿠키가 안 실린다 → CSRF 를 막는다. GET 으로 상태를 바꾸는 API 가 없어야 한다
 * - `Path=/`: 한 주소 아래 `/api/`(에이전트)·`/member/`(이 서버) 둘 다에 실려야 한다
 * - `Max-Age`: 토큰 만료와 같게 둔다. 브라우저가 만료된 쿠키를 알아서 버린다
 *
 * **읽기는 출처 검사를 거친다.** 쿠키는 브라우저가 알아서 붙이므로 다른 곳이 시킨 요청에도 실릴 수 있다
 * (`SameSite=Lax` 도 top-level GET 과 같은 사이트의 다른 서브도메인은 통과시킨다). 그래서 쿠키의 토큰은 브라우저가
 * `Sec-Fetch-Site` 로 same-origin(우리 페이지) 또는 none(주소창 직접 입력)이라고 알려 줄 때만 내준다 — 없거나
 * same-site·cross-site 면 `CrossSiteCookieException`(403). 인터셉터(access)와 `/auth/refresh`(refresh)가 같은 문을 지난다.
 * 브라우저는 이 헤더를 HTTPS·localhost 에서만 붙이므로 웹은 HTTPS 배포가 전제다 (쿠키도 Secure 라 평문 HTTP 엔 안 실린다).
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

    /** access 쿠키의 토큰. 없으면 null, 있는데 출처가 우리 페이지가 아니면 403. */
    fun accessToken(request: HttpServletRequest): String? = read(request, cookieProperties.accessName)

    /** refresh 쿠키의 토큰. 규칙은 access 와 같다. */
    fun refreshToken(request: HttpServletRequest): String? = read(request, cookieProperties.refreshName)

    private fun read(request: HttpServletRequest, name: String): String? {
        val value = request.cookies?.firstOrNull { it.name == name }?.value?.takeIf { it.isNotBlank() } ?: return null
        if (request.getHeader(SEC_FETCH_SITE)?.trim()?.lowercase() !in SAME_ORIGIN_VALUES) {
            throw CrossSiteCookieException()
        }
        return value
    }

    companion object {
        private const val SEC_FETCH_SITE = "Sec-Fetch-Site"
        // same-origin 은 우리 페이지가 부른 것, none 은 주소창에 직접 친 것. same-site(다른 서브도메인)·cross-site·없음은 거부
        private val SAME_ORIGIN_VALUES = setOf("same-origin", "none")
    }

    private fun cookie(name: String, value: String, maxAge: Duration): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookieProperties.secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
}
