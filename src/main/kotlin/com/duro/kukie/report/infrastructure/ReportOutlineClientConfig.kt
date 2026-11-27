package com.duro.kukie.report.infrastructure

import com.duro.kukie.global.config.properties.AnthropicProperties
import com.duro.kukie.global.config.properties.OpenAiProperties
import com.duro.kukie.global.config.properties.ReportProperties
import com.duro.kukie.report.application.port.out.ReportOutlineClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

@Configuration
class ReportOutlineClientConfig {

    /** Claude 키가 있으면 Claude 로, 없으면 OpenAI 로 정리한다. 둘 다 없으면 AI 없이 만든다. */
    @Bean
    fun reportOutlineClient(
        anthropicProperties: AnthropicProperties,
        openAiProperties: OpenAiProperties,
        reportProperties: ReportProperties,
        restClientBuilder: RestClient.Builder,
        objectMapper: ObjectMapper,
    ): ReportOutlineClient =
        if (anthropicProperties.enabled) {
            ClaudeReportOutlineClient(anthropicProperties, reportProperties, objectMapper)
        } else {
            OpenAiReportOutlineClient(restClientBuilder, openAiProperties, reportProperties, objectMapper)
        }
}
