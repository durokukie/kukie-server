package com.duro.kukie.global.security

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod

/**
 * 이 핸들러는 접근 토큰 말고 호스팅 에이전트 토큰(TokenType.HOSTING_AGENT)도 받는다. @Authenticated 와 함께 쓰고,
 * /hosting/agent 아래 핸들러에만 붙인다(HostingAgentTokenConventionTest). 에이전트 토큰은 대화 기록에 남을 수 있어서, 새거나
 * 프롬프트 주입을 당해도 앱 삭제 같은 다른 API 는 부를 수 없게 한다.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class HostingAgentToken

fun HandlerMethod.acceptsHostingAgentToken(): Boolean =
    hasMethodAnnotation(HostingAgentToken::class.java) ||
        AnnotatedElementUtils.hasAnnotation(beanType, HostingAgentToken::class.java)
