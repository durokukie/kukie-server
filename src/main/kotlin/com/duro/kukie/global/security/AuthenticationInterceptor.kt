package com.duro.kukie.global.security

import com.duro.kukie.auth.exception.CrossSiteCookieException
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.exception.UnauthorizedException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthenticationInterceptor(
    private val jwtTokenProvider: JwtTokenProvider,
    private val authCookies: AuthCookies,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod || handler.requiresAuthentication().not()) {
            return true
        }

        val token = resolveToken(request) ?: throw UnauthorizedException()
        val userId = jwtTokenProvider.getUserIdFromAccessToken(token) ?: throw InvalidTokenException()
        request.setAttribute(AUTHENTICATED_USER_ID, userId)

        return true
    }

    private fun HandlerMethod.requiresAuthentication(): Boolean =
        hasMethodAnnotation(Authenticated::class.java) ||
            AnnotatedElementUtils.hasAnnotation(beanType, Authenticated::class.java)

    /**
     * 헤더가 **있으면** 헤더만 본다 — 모양이 틀려도 쿠키로 넘어가지 않는다 (Basic 을 보낸 클라이언트가 조용히
     * 남의 쿠키 세션으로 도는 일이 없게). 헤더가 **없을 때만** 쿠키. 앱(Electron)과 에이전트(`/users/me` 조회)는
     * `Authorization: Bearer` 로 오고, 웹 브라우저는 httpOnly 쿠키로 온다 (`AuthCookies`). agent `auth.py` 와 같은 규칙.
     *
     * 쿠키는 브라우저가 알아서 붙이므로 다른 사이트가 시킨 요청에도 실릴 수 있다 (`SameSite=Lax` 도 top-level GET 은
     * 통과시킨다). 쿠키로만 인증된 요청은 같은 사이트에서 시작된 것만 받는다 — `Sec-Fetch-Site: cross-site` 면 거부.
     */
    private fun resolveToken(request: HttpServletRequest): String? {
        val header: String? = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null) return bearerToken(header)

        val cookieToken = authCookies.accessToken(request) ?: return null
        if (request.getHeader(SEC_FETCH_SITE)?.trim().equals(CROSS_SITE, ignoreCase = true)) {
            throw CrossSiteCookieException()
        }
        return cookieToken
    }

    private fun bearerToken(header: String): String? =
        header
            .takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val AUTHENTICATED_USER_ID = "authenticatedUserId"
        private const val BEARER_PREFIX = "Bearer "
        private const val SEC_FETCH_SITE = "Sec-Fetch-Site"
        private const val CROSS_SITE = "cross-site"
    }
}
