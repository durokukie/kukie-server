package com.duro.kukie.serving.application

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream

internal class ServingUsageReader {
    var finished = false
        private set
    var inputTokens: Long? = null
        private set
    var outputTokens: Long? = null
        private set
    private val line = ByteArrayOutputStream()
    private var overflow = false
    private val mapper = JsonMapper.builder().build()

    fun json(bytes: ByteArray) {
        runCatching {
            val usage = mapper.readTree(bytes)?.get("usage") ?: return
            inputTokens = tokenCount(usage.get("prompt_tokens"))
            outputTokens = tokenCount(usage.get("completion_tokens"))
        }
    }

    fun sse(bytes: ByteArray, size: Int) {
        for (index in 0 until size) {
            val value = bytes[index].toInt()
            if (value == 10) {
                if (!overflow) {
                    val text = line.toString(Charsets.UTF_8).trimEnd('\r')
                    if (text.startsWith("data:")) {
                        val data = text.substring(5).trimStart()
                        if (data == "[DONE]") finished = true else json(data.toByteArray(Charsets.UTF_8))
                    }
                }
                line.reset()
                overflow = false
            } else if (!overflow) {
                if (line.size() >= 65536) { overflow = true; line.reset() }
                else line.write(value)
            }
        }
    }

    private fun tokenCount(value: JsonNode?): Long? = value?.takeIf { it.isIntegralNumber && it.canConvertToLong() }?.longValue()?.takeIf { it >= 0 }
}
