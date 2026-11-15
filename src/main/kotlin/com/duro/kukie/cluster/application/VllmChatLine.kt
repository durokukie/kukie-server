package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.presentation.dto.response.ModelChatUsage
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** vLLM(OpenAI 호환)의 SSE 한 줄. 대화 내용이 든 줄이라 파싱에 실패해도 원문이나 원인 예외를 싣지 않는다. */
internal sealed interface VllmChatLine {

    data object End : VllmChatLine

    class Chunk(
        val content: String?,
        val reasoning: String?,
        val finishReason: String?,
        val usage: ModelChatUsage?,
    ) : VllmChatLine

    companion object {
        private const val DATA_PREFIX = "data:"
        private const val DONE = "[DONE]"
        private val jsonMapper = JsonMapper.builder().build()

        /** data 줄이 아니면(빈 줄, 주석, event 줄) null. 모델이 오류를 보냈거나 JSON 이 깨졌으면 GpuClusterApiFailedException. */
        fun parse(line: String): VllmChatLine? {
            if (!line.startsWith(DATA_PREFIX)) return null
            val data = line.removePrefix(DATA_PREFIX).trim()
            if (data == DONE) return End

            val root = try {
                jsonMapper.readTree(data)
            } catch (_: JacksonException) {
                throw GpuClusterApiFailedException()
            }
            if (!root.isObject || root.has("error")) throw GpuClusterApiFailedException()

            val choice = root.path("choices").path(0)
            val delta = choice.path("delta")
            return Chunk(
                content = delta.path("content").text(),
                // vLLM 버전에 따라 이름이 다르다
                reasoning = delta.path("reasoning").text() ?: delta.path("reasoning_content").text(),
                finishReason = choice.path("finish_reason").text(),
                usage = root.path("usage").takeIf { it.isObject }?.let {
                    ModelChatUsage(
                        promptTokens = it.path("prompt_tokens").count(),
                        completionTokens = it.path("completion_tokens").count(),
                        totalTokens = it.path("total_tokens").count(),
                    )
                },
            )
        }

        private fun JsonNode.text(): String? = takeIf { it.isString }?.asString()?.takeIf { it.isNotEmpty() }

        private fun JsonNode.count(): Long? = takeIf { it.isIntegralNumber }?.asLong()
    }
}
