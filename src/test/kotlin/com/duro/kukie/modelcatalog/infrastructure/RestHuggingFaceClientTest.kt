package com.duro.kukie.modelcatalog.infrastructure

import com.duro.kukie.global.config.properties.HuggingFaceProperties
import com.duro.kukie.modelcatalog.domain.GatedAccess
import com.duro.kukie.modelcatalog.exception.ModelCandidateRefreshFailedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.IOException

class RestHuggingFaceClientTest {

    private val builder = RestClient.builder().baseUrl(API)
    private val server = MockRestServiceServer.bindTo(builder).build()

    private fun client(token: String? = null) =
        RestHuggingFaceClient(builder.build(), HuggingFaceProperties(token = token))

    @Test
    fun `다운로드 순 목록을 한 번에 받아 필요한 칸을 읽는다`() {
        // given
        server.expect(requestTo(startsWith("$API/models?")))
            .andExpect(queryParam("pipeline_tag", "text-generation"))
            .andExpect(queryParam("sort", "downloads"))
            .andExpect(queryParam("direction", "-1"))
            .andExpect(queryParam("limit", "50"))
            .andExpect(requestTo(containsString("safetensors")))
            .andExpect(requestTo(not(containsString("usedStorage"))))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess(listing(), MediaType.APPLICATION_JSON))

        // when
        val models = client().fetchPopular(50)

        // then
        val (auto, manual, open) = models
        auto.id shouldBe "KRAFTON/Raon-1-mini-preview"
        auto.totalParams shouldBe 28_800_000_000
        auto.usedStorageBytes shouldBe 60_000_000_000
        auto.architectures.shouldContainExactly("Raon1MiniForCausalLM")
        auto.tags.shouldContainExactly("license:apache-2.0", "transformers")
        auto.gated shouldBe GatedAccess.AUTO
        auto.likes shouldBe 12
        auto.createdAt shouldBe "2026-10-01T00:00:00.000Z"
        manual.gated shouldBe GatedAccess.MANUAL
        open.gated shouldBe GatedAccess.NONE
        open.totalParams shouldBe null
        open.usedStorageBytes shouldBe null
        open.architectures shouldBe emptyList()
        open.likes shouldBe 0
        server.verify()
    }

    @Test
    fun `설정한 토큰을 Bearer 로 보낸다`() {
        // given
        server.expect(requestTo(startsWith("$API/models?")))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer hf_test_token"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        // when
        client(token = "hf_test_token").fetchPopular(50)

        // then
        server.verify()
    }

    @Test
    fun `빈 토큰은 보내지 않는다`() {
        // given
        server.expect(requestTo(startsWith("$API/models?")))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        // when
        client(token = "").fetchPopular(50)

        // then
        server.verify()
    }

    @Test
    fun `모델 하나를 id 로 받을 때는 레포 크기와 루트의 가중치 파일 크기까지 받는다`() {
        // given
        server.expect(requestTo(startsWith("$API/models/openai/gpt-oss-20b?")))
            .andExpect(requestTo(containsString("usedStorage")))
            .andExpect(requestTo(containsString("siblings")))
            .andExpect(queryParam("blobs", "true"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": "openai/gpt-oss-20b",
                      "gated": false,
                      "usedStorage": 41400000000,
                      "siblings": [
                        {"rfilename": "model-00001.safetensors", "size": 9000000000},
                        {"rfilename": "model-00002.safetensors", "size": 4800000000},
                        {"rfilename": "original/model.safetensors", "size": 13800000000},
                        {"rfilename": "config.json", "size": 1000}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        // when
        val model = client().fetchModel("openai/gpt-oss-20b")

        // then
        model.id shouldBe "openai/gpt-oss-20b"
        model.usedStorageBytes shouldBe 41_400_000_000
        model.weightBytes shouldBe 13_800_000_000
        server.verify()
    }

    @Test
    fun `호출 제한이나 서버 오류면 갱신 실패로 알린다`() {
        listOf(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE).forEach { status ->
            // given
            server.reset()
            server.expect(requestTo(startsWith("$API/models?"))).andRespond(withStatus(status))

            // when & then
            shouldThrow<ModelCandidateRefreshFailedException> { client().fetchPopular(50) }
        }
    }

    @Test
    fun `응답이 깨졌으면 갱신 실패로 알린다`() {
        // given
        server.expect(requestTo(startsWith("$API/models?"))).andRespond(withSuccess("<html>", MediaType.APPLICATION_JSON))

        // when & then
        shouldThrow<ModelCandidateRefreshFailedException> { client().fetchPopular(50) }
    }

    @Test
    fun `가중치를 consolidated 와 model 두 벌로 올린 레포는 한 벌만 더한다`() {
        // given
        server.expect(requestTo(startsWith("$API/models/mistralai/Mistral-Small?")))
            .andRespond(withSuccess(siblings("consolidated.safetensors" to 48, "model-00001-of-00002.safetensors" to 24, "model-00002-of-00002.safetensors" to 24), MediaType.APPLICATION_JSON))

        // when
        val model = client().fetchModel("mistralai/Mistral-Small")

        // then
        model.weightBytes shouldBe 48_000_000_000
    }

    @Test
    fun `consolidated 만 있거나 이름이 다른 조각이면 그대로 더한다`() {
        // given
        server.expect(requestTo(startsWith("$API/models/mistralai/Large?")))
            .andRespond(withSuccess(siblings("consolidated-00001-of-00002.safetensors" to 5, "consolidated-00002-of-00002.safetensors" to 5), MediaType.APPLICATION_JSON))
        server.expect(requestTo(startsWith("$API/models/Qwen/Layers?")))
            .andRespond(withSuccess(siblings("layers-0.safetensors" to 10, "layers-1.safetensors" to 20), MediaType.APPLICATION_JSON))

        // when & then
        client().fetchModel("mistralai/Large").weightBytes shouldBe 10_000_000_000
        client().fetchModel("Qwen/Layers").weightBytes shouldBe 30_000_000_000
    }

    @Test
    fun `칸의 타입이 다르거나 id 가 없으면 갱신 실패로 알린다`() {
        listOf("""[{"id": "openai/m", "likes": "많음"}]""", """[{"likes": 1}]""").forEach { body ->
            // given
            server.reset()
            server.expect(requestTo(startsWith("$API/models?"))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

            // when & then
            shouldThrow<ModelCandidateRefreshFailedException> { client().fetchPopular(50) }
        }
    }

    @Test
    fun `슬래시 없는 id 도 예외 없이 부른다`() {
        // given
        server.expect(requestTo(startsWith("$API/models/gpt2/?")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        // when & then
        shouldThrow<ModelCandidateRefreshFailedException> { client().fetchModel("gpt2") }
    }

    @Test
    fun `네트워크 오류면 갱신 실패로 알린다`() {
        // given
        server.expect(ExpectedCount.once(), requestTo(startsWith("$API/models/openai/m?")))
            .andRespond { throw ResourceAccessException("연결 실패", IOException("no route")) }

        // when & then
        shouldThrow<ModelCandidateRefreshFailedException> { client().fetchModel("openai/m") }
    }

    private fun siblings(vararg files: Pair<String, Int>): String {
        val entries = files.joinToString(",") { (name, gb) -> """{"rfilename": "$name", "size": ${gb}000000000}""" }
        return """{"id": "m/m", "siblings": [$entries]}"""
    }

    private fun listing() = """
        [
          {
            "id": "KRAFTON/Raon-1-mini-preview",
            "likes": 12,
            "gated": "auto",
            "createdAt": "2026-10-01T00:00:00.000Z",
            "tags": ["license:apache-2.0", "transformers"],
            "usedStorage": 60000000000,
            "safetensors": {"parameters": {"BF16": 28800000000}, "total": 28800000000},
            "config": {"architectures": ["Raon1MiniForCausalLM"], "model_type": "raon"},
            "trendingScore": 3
          },
          {"id": "meta-llama/Llama-3.3-70B", "gated": "manual"},
          {"id": "openai/no-metadata", "gated": false}
        ]
    """.trimIndent()

    companion object {
        private const val API = "https://example.test/api"
    }
}
