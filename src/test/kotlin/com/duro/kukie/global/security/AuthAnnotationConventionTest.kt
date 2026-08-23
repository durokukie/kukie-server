package com.duro.kukie.global.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.stereotype.Controller
import java.lang.reflect.Method

class AuthAnnotationConventionTest {

    @Test
    fun `AuthUser 파라미터는 Authenticated가 선언된 핸들러에서만 사용할 수 있다`() {
        val violations = findControllerClasses()
            .flatMap { it.declaredMethods.asList() }
            .filter { method -> method.parameters.any { it.isAnnotationPresent(AuthUser::class.java) } }
            .filterNot { it.requiresAuthentication() }
            .map { it.toGenericString() }

        assertThat(violations)
            .withFailMessage { "@AuthUser 파라미터는 @Authenticated가 선언된 핸들러에서만 사용할 수 있습니다: $violations" }
            .isEmpty()
    }

    private fun findControllerClasses(): List<Class<*>> =
        ClassPathScanningCandidateComponentProvider(false)
            .apply { addIncludeFilter(AnnotationTypeFilter(Controller::class.java)) }
            .findCandidateComponents(BASE_PACKAGE)
            .mapNotNull { it.beanClassName }
            .map { Class.forName(it) }

    private fun Method.requiresAuthentication(): Boolean =
        AnnotatedElementUtils.hasAnnotation(this, Authenticated::class.java) ||
            AnnotatedElementUtils.hasAnnotation(declaringClass, Authenticated::class.java)

    companion object {
        private const val BASE_PACKAGE = "com.duro.kukie"
    }
}
