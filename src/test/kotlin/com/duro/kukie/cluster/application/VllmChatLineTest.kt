package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.presentation.dto.response.ModelChatUsage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class VllmChatLineTest {

    @Test
    fun `답과 추론을 읽는다`() {
        // when
        val line = VllmChatLine.parse("""data: {"choices":[{"delta":{"content":"안녕","reasoning":"생각"}}]}""")

        // then
        line.shouldBeInstanceOf<VllmChatLine.Chunk>().run {
            content shouldBe "안녕"
            reasoning shouldBe "생각"
            finishReason shouldBe null
            usage shouldBe null
        }
    }

    @Test
    fun `옛 vLLM 의 reasoning_content 도 추론으로 읽는다`() {
        // when
        val line = VllmChatLine.parse("""data:{"choices":[{"delta":{"reasoning_content":"생각"}}]}""")

        // then
        line.shouldBeInstanceOf<VllmChatLine.Chunk>().reasoning shouldBe "생각"
    }

    @Test
    fun `빈 내용은 없는 것으로 보고 끝난 이유와 사용량을 읽는다`() {
        // when
        val line = VllmChatLine.parse(
            """data: {"choices":[{"delta":{"content":""},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}""",
        )

        // then
        line.shouldBeInstanceOf<VllmChatLine.Chunk>().run {
            content shouldBe null
            finishReason shouldBe "stop"
            usage shouldBe ModelChatUsage(3, 4, 7)
        }
    }

    @Test
    fun `choices 가 빈 사용량 청크를 읽는다`() {
        // when
        val line = VllmChatLine.parse("""data: {"choices":[],"usage":{"completion_tokens":4}}""")

        // then
        line.shouldBeInstanceOf<VllmChatLine.Chunk>().usage shouldBe ModelChatUsage(null, 4, null)
    }

    @Test
    fun `DONE 은 끝이다`() {
        VllmChatLine.parse("data: [DONE]") shouldBe VllmChatLine.End
    }

    @ParameterizedTest
    @ValueSource(strings = ["", ": keep-alive", "event: message", "id: 1"])
    fun `data 줄이 아니면 무시한다`(line: String) {
        VllmChatLine.parse(line) shouldBe null
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "data: 깨진 JSON 비밀문구",
            """data: ["비밀문구"]""",
            """data: {"error":{"message":"비밀문구"}}""",
        ],
    )
    fun `깨졌거나 오류인 줄은 원문과 원인 없이 실패한다`(line: String) {
        // when
        val failure = shouldThrow<GpuClusterApiFailedException> { VllmChatLine.parse(line) }

        // then
        failure.message!!.contains("비밀문구") shouldBe false
        failure.cause shouldBe null
    }
}
