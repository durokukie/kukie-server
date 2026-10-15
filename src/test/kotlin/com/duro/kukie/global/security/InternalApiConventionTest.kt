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

class InternalApiConventionTest {

    @Test
    fun `internal 경로의 핸들러는 InternalApi를 선언해야 한다`() {
        val violations = findHandlerMethods()
            .filter { it.isUnderInternal() }
            .filterNot { it.isInternalApi() }
            .map { it.toGenericString() }

        withClue("/internal/** 핸들러에는 @InternalApi 가 있어야 합니다") {
            violations.shouldBeEmpty()
        }
    }

    @Test
    fun `internal 밖의 핸들러는 InternalApi를 선언할 수 없다`() {
        val violations = findHandlerMethods()
            .filterNot { it.isUnderInternal() }
            .filter { it.isInternalApi() }
            .map { it.toGenericString() }

        withClue("@InternalApi 는 /internal/** 핸들러에만 선언할 수 있습니다") {
            violations.shouldBeEmpty()
        }
    }

    private fun findHandlerMethods(): List<Method> =
        ClassPathScanningCandidateComponentProvider(false)
            .apply { addIncludeFilter(AnnotationTypeFilter(Controller::class.java)) }
            .findCandidateComponents(BASE_PACKAGE)
            .mapNotNull { it.beanClassName }
            .map { Class.forName(it) }
            .flatMap { it.declaredMethods.asList() }
            .filter { AnnotatedElementUtils.hasAnnotation(it, RequestMapping::class.java) }

    private fun Method.isUnderInternal(): Boolean {
        val prefixes = declaringClass.mappedPaths().ifEmpty { listOf("") }
        val paths = mappedPaths().ifEmpty { listOf("") }

        return prefixes.any { prefix ->
            paths.any { path ->
                val fullPath = listOf(prefix, path).map { it.trim('/') }.filter { it.isNotEmpty() }.joinToString("/", prefix = "/")
                fullPath == INTERNAL || fullPath.startsWith("$INTERNAL/")
            }
        }
    }

    private fun AnnotatedElement.mappedPaths(): List<String> =
        AnnotatedElementUtils.findMergedAnnotation(this, RequestMapping::class.java)
            ?.path
            ?.toList()
            .orEmpty()

    private fun Method.isInternalApi(): Boolean =
        AnnotatedElementUtils.hasAnnotation(this, InternalApi::class.java) ||
            AnnotatedElementUtils.hasAnnotation(declaringClass, InternalApi::class.java)

    companion object {
        private const val BASE_PACKAGE = "com.duro.kukie"
        private const val INTERNAL = "/internal"
    }
}
