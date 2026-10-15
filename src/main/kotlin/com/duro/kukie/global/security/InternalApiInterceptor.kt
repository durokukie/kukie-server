package com.duro.kukie.global.security

import com.duro.kukie.auth.exception.InvalidInternalTokenException
import com.duro.kukie.global.config.properties.InternalProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.security.MessageDigest

@Component
class InternalApiInterceptor(
    internalProperties: InternalProperties,
) : HandlerInterceptor {

    private val expected = internalProperties.token.toByteArray(Charsets.UTF_8)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod || handler.isInternalApi().not()) {
            return true
        }

        val token = request.getHeader(INTERNAL_TOKEN_HEADER) ?: throw InvalidInternalTokenException()
        // 응답 시간으로 토큰이 새지 않게 상수 시간으로 비교한다
        if (!MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), expected)) {
            throw InvalidInternalTokenException()
        }

        return true
    }

    private fun HandlerMethod.isInternalApi(): Boolean =
        hasMethodAnnotation(InternalApi::class.java) ||
            AnnotatedElementUtils.hasAnnotation(beanType, InternalApi::class.java)

    companion object {
        const val INTERNAL_TOKEN_HEADER = "X-Kukie-Internal-Token"
    }
}
