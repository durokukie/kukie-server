package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "swagger")
data class SwaggerProperties(
    /** 프록시가 경로 접두사를 떼고 넘기는 환경에서 "Try it out" 이 부를 주소. 비우면 springdoc 이 요청에서 만든다. */
    val serverUrl: String? = null,
)
