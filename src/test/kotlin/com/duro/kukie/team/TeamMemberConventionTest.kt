package com.duro.kukie.team

import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.team.presentation.TeamMember
import com.duro.kukie.team.presentation.TeamRoleInterceptor
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.UUID

class TeamMemberConventionTest {

    @Test
    fun `teamId 경로 변수를 받는 핸들러는 RequireTeamRole 을 선언해야 한다`() {
        val violations = findHandlerMethods()
            .filter { it.teamIdParameter() != null }
            .filterNot { it.isAnnotationPresent(TeamMember::class.java) }
            .map { it.toGenericString() }

        withClue("{teamId} 경로 변수를 받는 핸들러에는 @RequireTeamRole 이 있어야 합니다") {
            violations.shouldBeEmpty()
        }
    }

    @Test
    fun `RequireTeamRole 은 Authenticated 핸들러의 UUID teamId 경로 변수에만 선언할 수 있다`() {
        val violations = findHandlerMethods()
            .filter { it.isAnnotationPresent(TeamMember::class.java) }
            .filterNot { it.teamIdParameter()?.type == UUID::class.java && it.requiresAuthentication() }
            .map { it.toGenericString() }

        withClue("@RequireTeamRole 은 @Authenticated 핸들러의 UUID 타입 {teamId} 경로 변수와 함께 써야 합니다") {
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

    private fun Method.teamIdParameter(): Parameter? {
        val names = parameterNameDiscoverer.getParameterNames(this) ?: return null

        return parameters.withIndex().firstOrNull { (index, parameter) ->
            val pathVariable = parameter.getAnnotation(PathVariable::class.java) ?: return@firstOrNull false
            val name = pathVariable.name.ifEmpty { pathVariable.value }.ifEmpty { names[index] }
            name == TeamRoleInterceptor.TEAM_ID
        }?.value
    }

    private fun Method.requiresAuthentication(): Boolean =
        AnnotatedElementUtils.hasAnnotation(this, Authenticated::class.java) ||
            AnnotatedElementUtils.hasAnnotation(declaringClass, Authenticated::class.java)

    companion object {
        private const val BASE_PACKAGE = "com.duro.kukie"
        private val parameterNameDiscoverer = DefaultParameterNameDiscoverer()
    }
}
