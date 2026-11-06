package com.duro.kukie.serving.presentation

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.serving.application.PrepareServingRelayService
import com.duro.kukie.serving.application.ServingRelay
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.json.JsonMapper

@RestController
class ServingRelayController(private val prepare: PrepareServingRelayService, private val relay: ServingRelay) {
    private val mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

    @GetMapping("/serving/v1/models")
    fun models(request: HttpServletRequest, response: HttpServletResponse) = handle(request, response, false)

    @PostMapping("/serving/v1/chat/completions")
    fun chat(request: HttpServletRequest, response: HttpServletResponse) = handle(request, response, true)

    private fun handle(request: HttpServletRequest, response: HttpServletResponse, chat: Boolean) {
        try {
            val headers = request.getHeaders("Authorization").toList()
            val header = headers.singleOrNull() ?: invalid()
            if (!header.startsWith("Bearer ", ignoreCase = true)) invalid()
            val target = prepare(header.substring(7))
            if (request.queryString != null) { badRequest(response); return }
            val body = if (chat) request.inputStream.readNBytes(MAX_BODY + 1) else null
            var model: String? = null
            var stream = false
            if (body != null) {
                if (body.size > MAX_BODY) { ServingApiErrors.write(response, 413, "SERVING_BODY_TOO_LARGE", "요청 본문이 너무 큽니다."); return }
                if (request.contentType?.substringBefore(';')?.trim()?.lowercase() != "application/json") { badRequest(response); return }
                val json = runCatching { mapper.readTree(body) }.getOrNull()
                if (json == null || !json.isObject || json.get("model")?.isString != true || json.get("stream")?.let { !it.isBoolean } == true) { badRequest(response); return }
                model = json.get("model").asString()
                stream = json.get("stream")?.asBoolean() ?: false
                if (target.eks != null && target.models.isEmpty()) throw ServingAccessException(ServingErrorCode.SERVING_MODEL_UNAVAILABLE)
                if (model !in target.models) throw ServingAccessException(ServingErrorCode.SERVING_MODEL_DENIED)
            }
            relay.start(request, response, target, body, model, stream)
        } catch (error: BusinessException) {
            ServingApiErrors.write(response, error.errorCode.status.value(), error.errorCode.code, error.errorCode.message)
        } catch (_: Exception) {
            ServingApiErrors.write(response, 502, "SERVING_UPSTREAM_FAILED", "API 중계가 완료되지 않았습니다.")
        }
    }

    private fun badRequest(response: HttpServletResponse) = ServingApiErrors.write(response, 400, "SERVING_BAD_REQUEST", "지원하지 않는 API 요청입니다.")
    private fun invalid(): Nothing = throw ServingAccessException(ServingErrorCode.SERVING_KEY_INVALID)
    private companion object { const val MAX_BODY = 1024 * 1024 }
}
