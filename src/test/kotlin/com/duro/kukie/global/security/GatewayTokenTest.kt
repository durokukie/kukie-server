package com.duro.kukie.global.security

import com.duro.kukie.auth.exception.InvalidInternalTokenException
import com.duro.kukie.global.config.properties.InternalProperties
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod

class GatewayTokenTest {
    @Test
    fun `공백과 에이전트와 같은 게이트웨이 토큰은 설정할 수 없다`() {
        // given when then
        for (token in listOf(" ", "agent-fixture")) {
            shouldThrow<IllegalArgumentException> { InternalProperties("agent-fixture", token) }
        }
    }

    @Test
    fun `게이트웨이 토큰 미설정은 닫혀 있고 InternalApi 누락도 인증을 우회하지 않는다`() {
        // given
        val handler = HandlerMethod(Endpoint(), Endpoint::class.java.getMethod("handle"))
        val request = MockHttpServletRequest().also { it.addHeader("X-Kukie-Internal-Token", "agent-fixture") }
        // when then
        shouldThrow<InvalidInternalTokenException> {
            InternalApiInterceptor(InternalProperties("agent-fixture")).preHandle(request, MockHttpServletResponse(), handler)
        }
        shouldThrow<InvalidInternalTokenException> {
            InternalApiInterceptor(InternalProperties("agent-fixture", "gateway-fixture")).preHandle(request, MockHttpServletResponse(), handler)
        }
    }

    @GatewayApi
    class Endpoint { fun handle() = Unit }
}
