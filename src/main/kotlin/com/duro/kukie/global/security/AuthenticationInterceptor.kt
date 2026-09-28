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

    /** 헤더가 있으면 헤더만 본다(모양이 틀려도 쿠키로 넘어가지 않음). 헤더가 없거나 비었을 때만 쿠키. */
    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)?.takeIf { it.isNotBlank() }
        if (header != null) return bearerToken(header)

        return authCookies.accessToken(request)
    }

    private fun bearerToken(header: String): String? =
        header
            .takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val AUTHENTICATED_USER_ID = "authenticatedUserId"
        private const val BEARER_PREFIX = "Bearer "
    }
}
