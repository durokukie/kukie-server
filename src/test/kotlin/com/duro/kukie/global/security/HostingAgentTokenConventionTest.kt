package com.duro.kukie.global.security

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method

class HostingAgentTokenConventionTest {

    @Test
    fun `에이전트 토큰은 로그인 허용 화면을 뺀 호스팅 에이전트 API 에서만 받는다`() {
        val violations = findHandlerMethods()
            .filter { it.acceptsAgentToken() }
            .filterNot { method -> method.fullPaths().all { it.startsWith(AGENT) && !it.startsWith(AGENT_LOGIN) } }
            .map { it.toGenericString() }

        withClue("@HostingAgentToken 은 $AGENT/** 에만(로그인 허용 $AGENT_LOGIN/** 제외) 선언할 수 있습니다") { violations.shouldBeEmpty() }
    }

    @Test
    fun `에이전트 토큰을 받는 핸들러는 인증을 요구한다`() {
        val violations = findHandlerMethods()
            .filter { it.acceptsAgentToken() }
            .filterNot { AnnotatedElementUtils.hasAnnotation(it, Authenticated::class.java) || AnnotatedElementUtils.hasAnnotation(it.declaringClass, Authenticated::class.java) }
            .map { it.toGenericString() }

        withClue("@HostingAgentToken 은 @Authenticated 와 함께 선언해야 합니다") { violations.shouldBeEmpty() }
    }

    private fun Method.acceptsAgentToken(): Boolean =
        AnnotatedElementUtils.hasAnnotation(this, HostingAgentToken::class.java) ||
            AnnotatedElementUtils.hasAnnotation(declaringClass, HostingAgentToken::class.java)

    private fun findHandlerMethods(): List<Method> =
        ClassPathScanningCandidateComponentProvider(false)
            .apply { addIncludeFilter(AnnotationTypeFilter(Controller::class.java)) }
            .findCandidateComponents("com.duro.kukie")
            .mapNotNull { it.beanClassName }
            .map { Class.forName(it) }
            .flatMap { it.declaredMethods.asList() }
            .filter { AnnotatedElementUtils.hasAnnotation(it, RequestMapping::class.java) }

    private fun Method.fullPaths(): List<String> =
        declaringClass.mappedPaths().ifEmpty { listOf("") }.flatMap { prefix ->
            mappedPaths().ifEmpty { listOf("") }.map { path ->
                listOf(prefix, path).map { it.trim('/') }.filter { it.isNotEmpty() }.joinToString("/", prefix = "/") + "/"
            }
        }

    private fun AnnotatedElement.mappedPaths(): List<String> =
        AnnotatedElementUtils.findMergedAnnotation(this, RequestMapping::class.java)?.path?.toList().orEmpty()

    companion object {
        private const val AGENT = "/hosting/agent/"
        private const val AGENT_LOGIN = "/hosting/agent/login/"
    }
}
