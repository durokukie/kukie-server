package com.duro.kukie.report.infrastructure

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.duro.kukie.global.config.properties.AnthropicProperties
import com.duro.kukie.global.config.properties.ReportProperties
import com.duro.kukie.report.application.ReportOutlinePrompt
import com.duro.kukie.report.application.port.out.ReportOutline
import com.duro.kukie.report.application.port.out.ReportOutlineClient
import com.duro.kukie.report.application.port.out.ReportSource
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

class ClaudeReportOutlineClient internal constructor(
    private val client: AnthropicClient,
    private val reportProperties: ReportProperties,
    private val objectMapper: ObjectMapper,
) : ReportOutlineClient {

    constructor(anthropicProperties: AnthropicProperties, reportProperties: ReportProperties, objectMapper: ObjectMapper) : this(
        AnthropicOkHttpClient.builder().apiKey(anthropicProperties.apiKey).timeout(TIMEOUT).maxRetries(MAX_RETRIES).build(),
        reportProperties,
        objectMapper,
    )

    override fun outline(source: ReportSource): ReportOutline {
        val message = client.beta().messages().create(request(source))
        // 거절했거나(refusal) 다 쓰지 못한(max_tokens) 답은 버린다
        val stopReason = message.stopReason().getOrNull()
        check(stopReason == BetaStopReason.END_TURN) { "보고서 정리 답을 받지 못했습니다: $stopReason" }
        val text = message.content().mapNotNull { it.text().getOrNull()?.text() }.joinToString("")

        return ReportOutlineAnswer.read(objectMapper.readValue(text, Map::class.java), source)
    }

    private fun request(source: ReportSource): MessageCreateParams = MessageCreateParams.builder()
        .model(reportProperties.claudeModel)
        .maxTokens(MAX_TOKENS)
        .system(ReportOutlinePrompt.SYSTEM)
        .addUserMessage(ReportOutlinePrompt.of(source))
        .outputConfig(
            BetaOutputConfig.builder()
                .effort(BetaOutputConfig.Effort.of(reportProperties.outlineEffort))
                .format(BetaJsonOutputFormat.builder().schema(JsonValue.from(ReportOutlineAnswer.SCHEMA)).build())
                .build(),
        )
        // 안전 분류기가 거절하면 Anthropic 이 권장하는 다른 모델로 서버에서 다시 돌린다
        .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
        .fallbacksDefault()
        .build()

    companion object {
        // 다시 시도까지 합쳐도 멈춘 보고서로 보는 시간(10분)보다 짧아야 같은 보고서를 두 번 만들지 않는다
        private val TIMEOUT: Duration = Duration.ofMinutes(3)
        private const val MAX_RETRIES = 1

        // 생각하는 데 쓰는 토큰도 여기에 든다
        private const val MAX_TOKENS = 16_000L
    }
}
