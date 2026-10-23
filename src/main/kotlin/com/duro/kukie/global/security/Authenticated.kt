package com.duro.kukie.global.security

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Authenticated

fun HandlerMethod.requiresAuthentication(): Boolean =
    hasMethodAnnotation(Authenticated::class.java) ||
        AnnotatedElementUtils.hasAnnotation(beanType, Authenticated::class.java)
