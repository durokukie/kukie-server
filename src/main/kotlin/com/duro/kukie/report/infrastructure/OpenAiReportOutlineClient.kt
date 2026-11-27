package com.duro.kukie.report.infrastructure

import com.duro.kukie.global.config.properties.OpenAiProperties
import com.duro.kukie.global.config.properties.ReportProperties
import com.duro.kukie.report.application.ReportOutlinePrompt
import com.duro.kukie.report.application.port.out.ReportOutline
import com.duro.kukie.report.application.port.out.ReportOutlineClient
import com.duro.kukie.report.application.port.out.ReportSource
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

class OpenAiReportOutlineClient internal constructor(
    private val restClient: RestClient,
    private val openAiProperties: OpenAiProperties,
    private val reportProperties: ReportProperties,
    private val objectMapper: ObjectMapper,
) : ReportOutlineClient {

    constructor(
        restClientBuilder: RestClient.Builder,
        openAiProperties: OpenAiProperties,
        reportProperties: ReportProperties,
        objectMapper: ObjectMapper,
    ) : this(
        restClientBuilder
            .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()).apply { setReadTimeout(READ_TIMEOUT) })
            .build(),
        openAiProperties,
        reportProperties,
        objectMapper,
    )

    override fun outline(source: ReportSource): ReportOutline? {
        if (!openAiProperties.enabled) return null

        val response = restClient.post()
            .uri(RESPONSES_URL)
            .headers { it.setBearerAuth(openAiProperties.apiKey) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(request(source))
            .retrieve()
            .body<Map<String, Any?>>()
        val text = response?.let(::outputText)?.takeIf { it.isNotBlank() } ?: error("보고서 정리 답을 받지 못했습니다")

        return ReportOutlineAnswer.read(objectMapper.readValue(text, Map::class.java), source)
    }

    private fun request(source: ReportSource) = mapOf(
        "model" to reportProperties.outlineModel,
        "instructions" to ReportOutlinePrompt.SYSTEM,
        "input" to ReportOutlinePrompt.of(source),
        "reasoning" to mapOf("effort" to reportProperties.outlineEffort),
        "max_output_tokens" to MAX_OUTPUT_TOKENS,
        "store" to false,
        "text" to mapOf("format" to FORMAT),
    )

    /** 다 쓰지 못했거나(incomplete) 거절한(refusal) 답은 버린다. */
    private fun outputText(response: Map<String, Any?>): String? {
        if (response["status"] != "completed") return null
        return (response["output"] as? List<*>).orEmpty()
            .mapNotNull { it as? Map<*, *> }
            .filter { it["type"] == "message" }
            .flatMap { (it["content"] as? List<*>).orEmpty() }
            .mapNotNull { it as? Map<*, *> }
            .filter { it["type"] == "output_text" }
            .joinToString("") { it["text"] as? String ?: "" }
    }

    companion object {
        private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)

        // 멈춘 보고서로 보는 시간(10분)보다 짧아야 같은 보고서를 두 번 만들지 않는다
        private val READ_TIMEOUT: Duration = Duration.ofMinutes(3)

        // 추론 토큰도 여기에 든다
        private const val MAX_OUTPUT_TOKENS = 16_000

        private val FORMAT = mapOf(
            "type" to "json_schema",
            "name" to "report_outline",
            "strict" to true,
            "schema" to ReportOutlineAnswer.SCHEMA,
        )
    }
}
