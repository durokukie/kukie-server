package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openai")
data class OpenAiProperties(
    /** 비어 있으면 AI 를 쓰는 기능(배포 실패 분석, 보고서 정리)이 AI 없이 동작한다. */
    val apiKey: String = "",
) {
    val enabled: Boolean
        get() = apiKey.isNotBlank()
}
