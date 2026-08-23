package com.duro.kukie.global.security

import com.duro.kukie.auth.exception.UnauthorizedException
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

@Component
class AuthUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        if (!parameter.hasParameterAnnotation(AuthUser::class.java)) {
            return false
        }
        check(parameter.parameterType == UUID::class.java) {
            "@AuthUser 파라미터는 UUID 타입이어야 합니다: ${parameter.executable.toGenericString()}"
        }
        return true
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UUID =
        webRequest.getAttribute(AuthenticationInterceptor.AUTHENTICATED_USER_ID, RequestAttributes.SCOPE_REQUEST) as? UUID
            ?: throw UnauthorizedException()
}
