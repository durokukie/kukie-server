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

    private fun resolveToken(request: HttpServletRequest): String? =
        request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val AUTHENTICATED_USER_ID = "authenticatedUserId"
        private const val BEARER_PREFIX = "Bearer "
    }
}
