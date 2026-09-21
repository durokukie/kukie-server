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
     * 남의 쿠키 세션으로 도는 일이 없게). 값이 빈 헤더는 존중할 뜻이 없으니 없는 것으로 친다. 헤더가 **없을 때만** 쿠키.
     * 앱(Electron)과 에이전트(`/users/me` 조회)는 `Authorization: Bearer` 로 오고, 웹 브라우저는 httpOnly 쿠키로 온다
     * (`AuthCookies`). agent `auth.py` 와 같은 규칙.
     *
     * 쿠키는 브라우저가 알아서 붙이므로 다른 사이트가 시킨 요청에도 실릴 수 있다 (`SameSite=Lax` 도 top-level GET 은
     * 통과시킨다). 쿠키로만 인증된 요청은 브라우저가 `Sec-Fetch-Site` 로 same-origin(우리 페이지) 또는 none(주소창 직접
     * 입력)이라고 알려 줄 때만 받는다. 헤더가 없어도, same-site(다른 서브도메인)여도 거부다(fail-closed). 웹은 리버스
     * 프록시로 이 서버와 같은 오리진이라 same-origin 만으로 다 돈다. 브라우저는 이 헤더를 **HTTPS·localhost 에서만** 붙이므로
     * 웹은 HTTPS 배포가 전제다 — 쿠키도 Secure 라 평문 HTTP 엔 애초에 안 실린다.
     */
    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)?.takeIf { it.isNotBlank() }
        if (header != null) return bearerToken(header)

        val cookieToken = authCookies.accessToken(request) ?: return null
        val site = request.getHeader(SEC_FETCH_SITE)?.trim()?.lowercase()
        if (site !in SAME_ORIGIN_VALUES) {
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
        // same-origin 은 우리 페이지가 부른 것, none 은 주소창에 직접 친 것. same-site(다른 서브도메인)·cross-site·없음은 거부
        private val SAME_ORIGIN_VALUES = setOf("same-origin", "none")
    }
}
