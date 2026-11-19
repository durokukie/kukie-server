package com.duro.kukie.hosting.infrastructure

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.StopReason
import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer
import com.duro.kukie.hosting.exception.HostingAnalysisFailedException
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AnthropicHostingFailureAnalyzer(
    private val hostingProperties: HostingProperties,
) : HostingFailureAnalyzer {

    private val client: AnthropicClient? = hostingProperties.analysisApiKey.takeIf { it.isNotBlank() }?.let {
        AnthropicOkHttpClient.builder()
            .apiKey(it)
            // API 앞 CloudFront 가 60초에 응답을 끊는다
            .timeout(TIMEOUT)
            .maxRetries(1)
            .build()
    }

    override val enabled: Boolean
        get() = client != null

    override fun analyze(system: String, prompt: String): String {
        val client = client ?: throw HostingAnalysisFailedException()
        val params = MessageCreateParams.builder()
            .model(hostingProperties.analysisModel)
            .maxTokens(MAX_TOKENS)
            .system(system)
            .addUserMessage(prompt)
            .build()
        val message = try {
            client.messages().create(params)
        } catch (e: AnthropicException) {
            log.warn("배포 실패 분석을 부르지 못했습니다: {}", e.message)
            throw HostingAnalysisFailedException()
        }
        val text = message.content().flatMap { block -> block.text().map { listOf(it.text()) }.orElse(emptyList()) }.joinToString("")
        if (text.isBlank() || message.stopReason().orElse(null) == StopReason.REFUSAL) throw HostingAnalysisFailedException()
        return text
    }

    @PreDestroy
    fun close() {
        client?.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnthropicHostingFailureAnalyzer::class.java)
        private val TIMEOUT: Duration = Duration.ofSeconds(40)
        private const val MAX_TOKENS = 1024L
    }
}
