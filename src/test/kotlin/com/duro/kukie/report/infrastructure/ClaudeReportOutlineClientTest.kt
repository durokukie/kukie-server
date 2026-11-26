package com.duro.kukie.report.infrastructure

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.duro.kukie.global.config.properties.ReportProperties
import com.duro.kukie.report.ReportFixture.source
import com.duro.kukie.report.ReportFixture.turn
import com.duro.kukie.report.ReportFixture.worklog
import com.duro.kukie.report.application.port.out.ReportOutline
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.util.UUID

class ClaudeReportOutlineClientTest {

    private class Received(val authorization: String?, val beta: String?, val body: Map<*, *>)

    private val mapper = JsonMapper.builder().build()
    private val received = mutableListOf<Received>()
    private var status = 200
    private var response = ""

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/messages") { exchange ->
            val headers = exchange.requestHeaders
            received += Received(headers.getFirst("Authorization"), headers.getFirst("anthropic-beta"), mapper.readValue(exchange.requestBody, Map::class.java))
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    private val client = ClaudeReportOutlineClient(
        AnthropicOkHttpClient.builder().apiKey("sk-ant-test").baseUrl("http://127.0.0.1:${server.address.port}").maxRetries(0).build(),
        ReportProperties(),
        mapper,
    )

    private val conversationId = UUID.randomUUID()
    private val source = source(worklog(turn(1), turn(2, "api 가 왜 죽어?"), id = conversationId))

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `자료를 보내 받은 답을 대화·턴 번호로 읽는다`() {
        // given
        val answer = """
            {
              "summary": "api 재시작 원인을 메모리 한도 초과로 확인함.",
              "tasks": [
                { "title": "api 재시작 원인 조사", "summary": "OOMKilled 반복.", "turns": [ { "conversation": 1, "seq": 2 } ], "followUps": [] }
              ]
            }
        """.trimIndent()
        response = message("end_turn", mapOf("type" to "thinking", "thinking" to "", "signature" to "sig"), mapOf("type" to "text", "text" to answer))

        // when
        val outline = client.outline(source)

        // then
        outline shouldBe ReportOutline(
            summary = "api 재시작 원인을 메모리 한도 초과로 확인함.",
            tasks = listOf(ReportOutline.Task("api 재시작 원인 조사", "OOMKilled 반복.", listOf(ReportOutline.TurnRef(conversationId, 2)), emptyList())),
        )
        val sent = received.single()
        sent.authorization shouldBe "Bearer sk-ant-test"
        sent.beta shouldBe "server-side-fallback-2026-07-01"
        sent.body["model"] shouldBe "claude-sonnet-5-5"
        sent.body["fallbacks"] shouldBe "default"
        sent.body["system"].toString() shouldContain "Kubernetes 운영 기록"
        sent.body["messages"].toString() shouldContain "## 대화 1: api 점검\n### 턴 1 (completed)"
        val outputConfig = sent.body["output_config"] as Map<*, *>
        outputConfig["effort"] shouldBe "medium"
        val format = outputConfig["format"] as Map<*, *>
        format["type"] shouldBe "json_schema"
        (format["schema"] as Map<*, *>)["required"] shouldBe listOf("summary", "tasks")
    }

    @Test
    fun `거절했거나 다 쓰지 못했거나 부르지 못한 답은 예외로 알린다`() {
        listOf(
            200 to message("refusal"),
            200 to message("max_tokens", mapOf("type" to "text", "text" to """{"summary": "쓰다가""")),
            400 to """{"type": "error", "error": {"type": "invalid_request_error", "message": "bad"}}""",
        ).forEach { (code, body) ->
            // given
            status = code
            response = body

            // when & then
            shouldThrow<Exception> { client.outline(source) }
        }
    }

    private fun message(stopReason: String, vararg content: Map<String, Any?>): String = mapper.writeValueAsString(
        mapOf(
            "id" to "msg_1",
            "type" to "message",
            "role" to "assistant",
            "model" to "claude-sonnet-5-5",
            "content" to content.toList(),
            "stop_reason" to stopReason,
            "stop_sequence" to null,
            "usage" to mapOf("input_tokens" to 10, "output_tokens" to 20),
        ),
    )
}
