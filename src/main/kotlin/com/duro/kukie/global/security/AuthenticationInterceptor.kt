package com.duro.kukie.global.security

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
     * 헤더가 먼저, 없을 때만 쿠키. 앱(Electron)과 에이전트(`/users/me` 조회)는 `Authorization: Bearer` 로 오고,
     * 웹 브라우저는 httpOnly 쿠키로 온다 (`AuthCookies`). 둘 다 있으면 헤더가 이긴다 — 헤더는 부르는 쪽이
     * 이번 요청에 일부러 붙인 값이고 쿠키는 브라우저가 알아서 붙이는 값이라서다.
     */
    private fun resolveToken(request: HttpServletRequest): String? =
        bearerToken(request) ?: authCookies.accessToken(request)

    private fun bearerToken(request: HttpServletRequest): String? =
        request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val AUTHENTICATED_USER_ID = "authenticatedUserId"
        private const val BEARER_PREFIX = "Bearer "
    }
}
