package com.duro.kukie.global.security

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class InternalApi

fun HandlerMethod.isInternalApi(): Boolean =
    hasMethodAnnotation(InternalApi::class.java) ||
        AnnotatedElementUtils.hasAnnotation(beanType, InternalApi::class.java)
