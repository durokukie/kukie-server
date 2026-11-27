package com.duro.kukie.report.infrastructure

import com.duro.kukie.global.config.properties.OpenAiProperties
import com.duro.kukie.global.config.properties.ReportProperties
import com.duro.kukie.report.ReportFixture.source
import com.duro.kukie.report.ReportFixture.turn
import com.duro.kukie.report.ReportFixture.worklog
import com.duro.kukie.report.application.port.out.ReportOutline
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.containsString
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
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class OpenAiReportOutlineClientTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val mapper = JsonMapper.builder().build()
    private val client = OpenAiReportOutlineClient(builder.build(), OpenAiProperties(apiKey = "sk-test"), ReportProperties(), mapper)

    private val conversationId = UUID.randomUUID()
    private val source = source(worklog(turn(1), turn(2, "api 가 왜 죽어?"), id = conversationId))

    @Test
    fun `자료를 보내 받은 답을 대화·턴 번호로 읽는다`() {
        // given
        val answer = """
            {
              "summary": "api 가 메모리 부족으로 죽었다.",
              "tasks": [
                {
                  "title": "장애 조사",
                  "summary": "원인을 찾았다.",
                  "turns": [ { "conversation": 1, "seq": 2 }, { "conversation": 7, "seq": 1 } ],
                  "followUps": [ "메모리 한도 올리기", " " ]
                },
                { "title": " ", "summary": "", "turns": [], "followUps": [] }
              ]
            }
        """.trimIndent()
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test"))
            .andExpect(jsonPath("$.model").value("gpt-6-luna"))
            .andExpect(jsonPath("$.input").value(containsString("## 대화 1: api 점검\n### 턴 1 (completed)")))
            .andExpect(jsonPath("$.text.format.type").value("json_schema"))
            .andExpect(jsonPath("$.text.format.strict").value(true))
            .andExpect(jsonPath("$.text.format.schema.required[1]").value("tasks"))
            .andRespond(withSuccess(completed(answer), MediaType.APPLICATION_JSON))

        // when
        val outline = client.outline(source)

        // then
        outline shouldBe ReportOutline(
            summary = "api 가 메모리 부족으로 죽었다.",
            tasks = listOf(
                ReportOutline.Task("장애 조사", "원인을 찾았다.", listOf(ReportOutline.TurnRef(conversationId, 2)), listOf("메모리 한도 올리기")),
            ),
        )
        server.verify()
    }

    @Test
    fun `부르지 못했거나 다 쓰지 못했거나 JSON 이 아닌 답은 예외로 알린다`() {
        listOf(
            withStatus(HttpStatus.TOO_MANY_REQUESTS),
            withSuccess("""{"status": "incomplete", "output": []}""", MediaType.APPLICATION_JSON),
            withSuccess(completed("정리할 수 없습니다"), MediaType.APPLICATION_JSON),
        ).forEach { response ->
            // given
            server.reset()
            server.expect(requestTo(URL)).andRespond(response)

            // when & then
            shouldThrow<Exception> { client.outline(source) }
        }
    }

    @Test
    fun `키가 없으면 부르지 않고 null 을 준다`() {
        // given
        val off = OpenAiReportOutlineClient(builder.build(), OpenAiProperties(), ReportProperties(), mapper)

        // when & then
        off.outline(source) shouldBe null
        server.verify()
    }

    private fun completed(text: String): String = mapper.writeValueAsString(
        mapOf(
            "status" to "completed",
            "output" to listOf(
                mapOf("type" to "reasoning", "summary" to emptyList<Any>()),
                mapOf("type" to "message", "content" to listOf(mapOf("type" to "output_text", "text" to text))),
            ),
        ),
    )

    companion object {
        private const val URL = "https://api.openai.com/v1/responses"
    }
}
