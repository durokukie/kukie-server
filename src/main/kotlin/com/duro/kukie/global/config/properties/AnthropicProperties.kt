package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "anthropic")
data class AnthropicProperties(
    /** 있으면 보고서를 OpenAI 대신 Claude 로 정리한다. */
    val apiKey: String = "",
) {
    val enabled: Boolean
        get() = apiKey.isNotBlank()
}
