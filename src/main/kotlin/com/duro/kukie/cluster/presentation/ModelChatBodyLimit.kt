package com.duro.kukie.cluster.presentation

import com.duro.kukie.cluster.exception.ModelChatTooLargeException
import com.duro.kukie.cluster.presentation.dto.request.ModelChatRequest
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Type

/** JSON 으로 풀어 메모리에 올리기 전에 크기를 본다. Content-Length 가 없는 요청도 있어 실제로 읽어서 잰다. */
@ControllerAdvice
class ModelChatBodyLimit : RequestBodyAdviceAdapter() {

    override fun supports(
        methodParameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = targetType == ModelChatRequest::class.java

    override fun beforeBodyRead(
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>,
    ): HttpInputMessage {
        val bytes = inputMessage.body.readNBytes(MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) throw ModelChatTooLargeException()
        return object : HttpInputMessage {
            override fun getBody(): InputStream = ByteArrayInputStream(bytes)

            override fun getHeaders(): HttpHeaders = inputMessage.headers
        }
    }

    companion object {
        internal const val MAX_BYTES = 1024 * 1024
    }
}
