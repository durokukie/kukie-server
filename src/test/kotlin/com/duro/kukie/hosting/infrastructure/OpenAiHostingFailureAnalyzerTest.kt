package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.config.properties.OpenAiProperties
import com.duro.kukie.hosting.exception.HostingAnalysisFailedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OpenAiHostingFailureAnalyzerTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val analyzer = OpenAiHostingFailureAnalyzer(builder.build(), HostingProperties(), OpenAiProperties(apiKey = "sk-test"))

    @Test
    fun `설정한 모델에 JSON 답을 요청하고 메시지의 글을 준다`() {
        // given
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test"))
            .andExpect(jsonPath("$.model").value("gpt-6-luna"))
            .andExpect(jsonPath("$.reasoning.effort").value("medium"))
            .andExpect(jsonPath("$.instructions").value("system"))
            .andExpect(jsonPath("$.input").value("prompt"))
            .andExpect(jsonPath("$.text.format.type").value("json_schema"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "completed",
                      "output": [
                        { "type": "reasoning", "summary": [] },
                        { "type": "message", "content": [ { "type": "output_text", "text": "{\"cause\": \"x\", \"fixes\": []}" } ] }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        // when & then
        analyzer.analyze("system", "prompt") shouldBe """{"cause": "x", "fixes": []}"""
        server.verify()
    }

    @Test
    fun `부르지 못했거나 다 쓰지 못했거나 거절한 답은 실패로 본다`() {
        listOf(
            withStatus(HttpStatus.TOO_MANY_REQUESTS),
            withSuccess("""{"status": "incomplete", "output": []}""", MediaType.APPLICATION_JSON),
            withSuccess(
                """{"status": "completed", "output": [{"type": "message", "content": [{"type": "refusal", "refusal": "no"}]}]}""",
                MediaType.APPLICATION_JSON,
            ),
        ).forEach { response ->
            // given
            server.reset()
            server.expect(requestTo(URL)).andRespond(response)

            // when & then
            shouldThrow<HostingAnalysisFailedException> { analyzer.analyze("system", "prompt") }
        }
    }

    @Test
    fun `키가 없으면 꺼져 있고 부르지 않는다`() {
        // given
        val off = OpenAiHostingFailureAnalyzer(builder.build(), HostingProperties(), OpenAiProperties())

        // when & then
        off.enabled shouldBe false
        shouldThrow<HostingAnalysisFailedException> { off.analyze("system", "prompt") }
    }

    companion object {
        private const val URL = "https://api.openai.com/v1/responses"
    }
}
