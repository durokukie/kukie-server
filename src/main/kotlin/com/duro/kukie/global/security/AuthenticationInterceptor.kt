package com.duro.kukie.global.security

import com.duro.kukie.activity.application.RecordActiveUserService
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.auth.exception.UnauthorizedException
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthenticationInterceptor(
    private val jwtTokenProvider: JwtTokenProvider,
    private val authCookies: AuthCookies,
    private val hostingAgentKeyAuthenticator: HostingAgentKeyAuthenticator,
    private val recordActiveUserService: RecordActiveUserService,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod || handler.requiresAuthentication().not()) {
            return true
        }
        // 스트림이 끝나면 같은 요청이 인터셉터를 한 번 더 지난다. 그사이 만료된 토큰으로 이미 보낸 응답을 401 로 망치지 않게 입구에서만 본다
        if (request.dispatcherType == DispatcherType.ASYNC) {
            return true
        }

        val token = resolveToken(request) ?: throw UnauthorizedException()
        val subject = hostingAgentKey(token, handler)
            ?: jwtTokenProvider.parseAccessToken(token)
            ?: jwtTokenProvider.getUserIdFromHostingAgentToken(token)?.takeIf { handler.acceptsHostingAgentToken() }?.let { TokenSubject(it, null) }
            ?: throw InvalidTokenException()
        request.setAttribute(AUTHENTICATED_USER_ID, subject.userId)
        subject.sessionId?.let { request.setAttribute(AUTHENTICATED_SESSION_ID, it) }
        recordActiveUserService(subject.userId)

        return true
    }

    private fun hostingAgentKey(token: String, handler: HandlerMethod): TokenSubject? {
        if (!token.startsWith(HostingAgentKeyAuthenticator.PREFIX)) return null
        if (!handler.acceptsHostingAgentToken()) throw InvalidTokenException()
        return TokenSubject(hostingAgentKeyAuthenticator.userIdOf(token) ?: throw InvalidTokenException(), null)
    }

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
        const val AUTHENTICATED_SESSION_ID = "authenticatedSessionId"
        private const val BEARER_PREFIX = "Bearer "
    }
}
