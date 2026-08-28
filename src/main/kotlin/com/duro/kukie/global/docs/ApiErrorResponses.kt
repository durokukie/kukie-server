package com.duro.kukie.global.docs

import com.duro.kukie.global.exception.BusinessException
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiErrorResponses(vararg val value: KClass<out BusinessException>)
