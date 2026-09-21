package com.duro.kukie.global.config

import com.duro.kukie.global.security.AuthUserArgumentResolver
import com.duro.kukie.global.security.AuthenticationInterceptor
import com.duro.kukie.team.presentation.TeamRoleInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val authenticationInterceptor: AuthenticationInterceptor,
    private val teamRoleInterceptor: TeamRoleInterceptor,
    private val authUserArgumentResolver: AuthUserArgumentResolver,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authenticationInterceptor)
        registry.addInterceptor(teamRoleInterceptor)
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authUserArgumentResolver)
    }
}
