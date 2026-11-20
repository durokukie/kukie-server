package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer
import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer.Answer
import com.duro.kukie.hosting.exception.HostingAnalysisFailedException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import java.net.http.HttpClient
import java.time.Duration

/** OpenAI Responses API 로 분석한다. 답은 원인과 고칠 방법의 JSON 으로 받는다(structured outputs). */
@Component
class OpenAiHostingFailureAnalyzer internal constructor(
    private val restClient: RestClient,
    private val hostingProperties: HostingProperties,
) : HostingFailureAnalyzer {

    // API 앞 CloudFront 가 60초에 응답을 끊는다
    @Autowired
    constructor(restClientBuilder: RestClient.Builder, hostingProperties: HostingProperties) : this(
        restClientBuilder
            .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()).apply { setReadTimeout(READ_TIMEOUT) })
            .build(),
        hostingProperties,
    )

    private val log = logger()

    override val enabled: Boolean
        get() = hostingProperties.analysisApiKey.isNotBlank()

    override fun analyze(system: String, prompt: String, answer: Answer): String {
        if (!enabled) throw HostingAnalysisFailedException()
        val response = try {
            restClient.post()
                .uri(RESPONSES_URL)
                .headers { it.setBearerAuth(hostingProperties.analysisApiKey) }
                .contentType(MediaType.APPLICATION_JSON)
                .body(request(system, prompt, answer))
                .retrieve()
                .body<Map<String, Any?>>()
        } catch (exception: RestClientException) {
            log.warn("배포 실패 분석을 부르지 못했습니다: {}", exception.message)
            throw HostingAnalysisFailedException()
        }
        return response?.let(::outputText)?.takeIf { it.isNotBlank() } ?: throw HostingAnalysisFailedException()
    }

    private fun request(system: String, prompt: String, answer: Answer) = mapOf(
        "model" to hostingProperties.analysisModel,
        "instructions" to system,
        "input" to prompt,
        "reasoning" to mapOf("effort" to hostingProperties.analysisEffort),
        "max_output_tokens" to MAX_OUTPUT_TOKENS,
        "store" to false,
        "text" to mapOf("format" to if (answer == Answer.START) START_FORMAT else ANSWER_FORMAT),
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
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(40)

        // 추론 토큰도 여기에 든다
        private const val MAX_OUTPUT_TOKENS = 8000

        private val ANSWER_FORMAT = mapOf(
            "type" to "json_schema",
            "name" to "failure_analysis",
            "strict" to true,
            "schema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "cause" to mapOf("type" to "string"),
                    "fixes" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                ),
                "required" to listOf("cause", "fixes"),
                "additionalProperties" to false,
            ),
        )

        private val START_FORMAT = mapOf(
            "type" to "json_schema",
            "name" to "start_command",
            "strict" to true,
            "schema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "start" to mapOf("type" to "string"),
                    "confident" to mapOf("type" to "boolean"),
                    "reason" to mapOf("type" to "string"),
                ),
                "required" to listOf("start", "confident", "reason"),
                "additionalProperties" to false,
            ),
        )
    }
}
