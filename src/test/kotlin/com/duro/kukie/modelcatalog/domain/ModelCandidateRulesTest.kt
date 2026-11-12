package com.duro.kukie.modelcatalog.domain

import com.duro.kukie.global.config.properties.HuggingFaceProperties
import com.duro.kukie.modelcatalog.ModelCatalogFixture.gptOss120b
import com.duro.kukie.modelcatalog.ModelCatalogFixture.model
import com.duro.kukie.modelcatalog.infrastructure.RestHuggingFaceClient
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.LocalDate

class ModelCandidateRulesTest {

    @Test
    fun `2026-11-03 트렌딩 50개에서 원작자의 범위 안 모델 셋만 남는다`() {
        // given
        val listing = trendingFixture()

        // when
        val selection = ModelCandidateRules.select(listing)

        // then
        listing.size shouldBe 50
        selection.kept.map { it.id }.shouldContainExactly(
            "Nanbeige/Nanbeige4.3-33B",
            "RecursiveSI/flint",
            "openai/gpt-oss-120b",
        )
        selection.dropped.size shouldBe 47
    }

    @Test
    fun `그날 상위 15개의 재업로드와 소형 모델이 걸러진다`() {
        // given
        val dropped = ModelCandidateRules.select(trendingFixture()).dropped

        // then
        listOf(
            "OBLITERATUS/DeepSeek-V4.1-Flash-1017-obliterated" to "원작자 아님",
            "ISTA-DASLab/DeepSeek-V4.1-Flash-1017-GSQ-RCO-GGUF" to "원작자 아님",
            "AtomicChat/LFM3-1.7B-Uncensored-GGUF" to "원작자 아님",
            "orcarouter/DeepSeek-V4.1-Flash-1017-abliterated-GGUF" to "원작자 아님",
            "dealignai/GLM-5.3-CYBERSECURITY-FP8" to "원작자 아님",
            "LiquidAI/LFM3-1.7B" to "크기 1.7B",
            "LiquidAI/LFM3-1.7B-GGUF" to "변형 이름 (GGUF)",
            "XHToken/Spark-X2.6-4B" to "원작자 아님",
            "nvidia/NVIDIA-Nemotron-3.5-Bolt-E4B-4GB" to "파라미터 수 없음",
            "zai-org/GLM-5.3" to "크기 753.3B",
            "inclusionAI/Ling-3.1-flash-1019" to "크기 572.1B",
            "meta-llama/Llama-3.1-8B-Instruct" to "크기 8.0B",
        ).forEach { (modelId, reason) -> dropped.getValue(modelId) shouldStartWith reason }
    }

    @Test
    fun `후보에 파라미터 수와 라이선스와 vLLM 지원을 담는다`() {
        // given
        val listing = trendingFixture()

        // when
        val candidate = ModelCandidateRules.select(listing).kept.single { it.id == "openai/gpt-oss-120b" }

        // then
        candidate.paramsB shouldBe 116.8
        candidate.sizeGb shouldBe null
        candidate.architecture shouldBe "GptOssForCausalLM"
        candidate.license shouldBe "apache-2.0"
        candidate.licenseNote shouldBe null
        candidate.vllmSupport shouldBe VllmSupport.UPSTREAM
        candidate.gated shouldBe GatedAccess.NONE
        candidate.registeredOn shouldBe LocalDate.of(2025, 8, 4)
    }

    @Test
    fun `파일 크기로 내려받을 크기와 가중치 크기를 채운다`() {
        // given
        val candidate = keptOne(model("openai/gpt-oss-20b"))

        // when
        val filled = ModelCandidateRules.withRepoFiles(candidate, model("openai/gpt-oss-20b", usedStorageGb = 41.44, weightGb = 13.84))

        // then
        filled.sizeGb shouldBe 41.4
        filled.weightGb shouldBe 13.8
    }

    @Test
    fun `가중치 크기로 들어가는 GPU 를 정한다`() {
        // given
        val candidate = keptOne(model("openai/m"))

        // when
        val fits = listOf(13.8, 35.0, 35.1, 66.2, 73.0, 79.6).map {
            ModelCandidateRules.withRepoFiles(candidate, model("openai/m", weightGb = it)).fitsGpus
        }

        // then
        fits.shouldContainExactly(
            listOf("l40s", "pro6000"),
            listOf("l40s", "pro6000"),
            listOf("pro6000"),
            listOf("pro6000"),
            listOf("pro6000"),
            emptyList(),
        )
    }

    @Test
    fun `가중치 크기를 모르면 들어가는 GPU 가 없다`() {
        // given
        val candidate = keptOne(model("openai/m"))

        // when
        val filled = ModelCandidateRules.withRepoFiles(candidate, model("openai/m", weightGb = null))

        // then
        filled.fitsGpus.shouldBeEmpty()
        filled.weightGb shouldBe null
    }

    @Test
    fun `크기 범위 밖이면 버린다`() {
        listOf(19.9, 130.1).forEach {
            // when
            val selection = ModelCandidateRules.select(listOf(model("openai/m", paramsB = it)))

            // then
            selection.kept.shouldBeEmpty()
            selection.dropped.getValue("openai/m") shouldStartWith "크기"
        }
    }

    @Test
    fun `원작자 계정이라도 변형 이름이면 버린다`() {
        listOf("GGUF", "abliterated", "Uncensored", "AWQ", "mlx").forEach {
            // when
            val selection = ModelCandidateRules.select(listOf(model("Qwen/Qwen3.8-27B-$it")))

            // then
            selection.kept.shouldBeEmpty()
            selection.dropped.getValue("Qwen/Qwen3.8-27B-$it") shouldStartWith "변형 이름"
        }
    }

    @Test
    fun `칸보다 긴 값이 있는 모델은 버려 갱신이 실패하지 않게 한다`() {
        // given
        val models = listOf(
            model("openai/long-license", license = "x".repeat(ModelCandidate.MAX_LICENSE_LENGTH + 1)),
            model("openai/long-architecture", architecture = "x".repeat(ModelCandidate.MAX_ARCHITECTURE_LENGTH + 1)),
            model("openai/" + "x".repeat(ModelCandidate.MAX_MODEL_ID_LENGTH)),
            model("openai/ok"),
        )

        // when
        val selection = ModelCandidateRules.select(models)

        // then
        selection.kept.map { it.id }.shouldContainExactly("openai/ok")
        selection.dropped.values.toSet() shouldBe setOf("저장할 수 없는 긴 값")
    }

    @Test
    fun `같은 모델이 두 번 오면 한 번만 남긴다`() {
        // when
        val selection = ModelCandidateRules.select(listOf(model("openai/m"), model("openai/m")))

        // then
        selection.kept.map { it.id }.shouldContainExactly("openai/m")
    }

    @Test
    fun `계정 없는 모델 id 는 버린다`() {
        // when
        val selection = ModelCandidateRules.select(listOf(model("google"), model("gpt2")))

        // then
        selection.kept.shouldBeEmpty()
        selection.dropped.getValue("google") shouldStartWith "원작자 아님"
    }

    @Test
    fun `원작자가 올린 FP8 은 남긴다`() {
        // when
        val candidate = keptOne(model("Qwen/Qwen3.8-27B-FP8", architecture = "Qwen3_5ForConditionalGeneration"))

        // then
        candidate.vllmSupport shouldBe VllmSupport.UPSTREAM
    }

    @Test
    fun `원작자 계정은 대소문자를 가리지 않는다`() {
        // when
        val candidate = keptOne(model("qwen/Qwen3-32B"))

        // then
        candidate.id shouldBe "qwen/Qwen3-32B"
    }

    @Test
    fun `비상업 라이선스는 버린다`() {
        listOf("cc-by-nc-4.0", "cc-by-nc-sa-4.0", "exaone", "nvidia-research").forEach {
            // when
            val selection = ModelCandidateRules.select(listOf(model("openai/m", license = it)))

            // then
            selection.kept.shouldBeEmpty()
            selection.dropped.getValue("openai/m") shouldBe "비상업 라이선스 ($it)"
        }
    }

    @Test
    fun `라이선스 조건을 나눈다`() {
        // when
        val notes = listOf("apache-2.0", "mit", "llama3.1", "gemma", "other", null)
            .map { keptOne(model("openai/m", license = it)).run { license to licenseNote } }

        // then
        notes.shouldContainExactly(
            "apache-2.0" to null,
            "mit" to null,
            "llama3.1" to "조건 있음",
            "gemma" to "조건 있음",
            "other" to "확인 필요",
            "?" to "확인 필요",
        )
    }

    @Test
    fun `vLLM 지원을 나눈다`() {
        // when
        val support = listOf("LlamaForCausalLM", "Raon1MiniForCausalLM", "Glm5NextForConditionalGeneration", null)
            .map { keptOne(model("openai/m", architecture = it)).vllmSupport }

        // then
        support.shouldContainExactly(VllmSupport.UPSTREAM, VllmSupport.FORK, VllmSupport.UNKNOWN, VllmSupport.UNKNOWN)
    }

    @Test
    fun `gated 를 그대로 담는다`() {
        // when
        val candidate = keptOne(model("KRAFTON/Raon-1-mini-preview", gated = GatedAccess.MANUAL))

        // then
        candidate.gated shouldBe GatedAccess.MANUAL
    }

    @Test
    fun `검증된 모델은 L40S 1장 동시 32 인 gpt-oss-20b 와 4장 동시 8 인 gpt-oss-120b 다`() {
        // when
        val verified = VerifiedModels.MODELS

        // then
        verified.keys.toList().shouldContainExactly("openai/gpt-oss-20b", "openai/gpt-oss-120b")
        verified.getValue("openai/gpt-oss-20b").run { Triple(gpu, gpus, maxConcurrency) } shouldBe Triple("l40s", 1, 32)
        verified.getValue("openai/gpt-oss-120b").run { Triple(gpu, gpus, maxConcurrency) } shouldBe Triple("l40s", 4, 8)
        verified.getValue("openai/gpt-oss-120b").measuredOn shouldBe "2026-11-04"
    }

    @Test
    fun `실측한 장수에 들어가는지는 GPU 1장 크기에 장수를 곱해 본다`() {
        // given
        val verified = VerifiedModel(gpu = "l40s", maxConcurrency = 8, measuredOn = "2026-11-04", gpus = 4)
        val candidate = keptOne(model("openai/gpt-oss-120b", paramsB = 116.8))

        // when & then
        ModelCandidateRules.fitsMeasured(candidate.copy(weightGb = 140.0), verified) shouldBe true
        ModelCandidateRules.fitsMeasured(candidate.copy(weightGb = 140.1), verified) shouldBe false
        ModelCandidateRules.fitsMeasured(candidate.copy(weightGb = null), verified) shouldBe false
        ModelCandidateRules.fitsMeasured(candidate.copy(weightGb = 10.0), verified.copy(gpu = "unknown")) shouldBe false
    }

    @Test
    fun `fitsGpus 는 여러 장 모델에도 1장 기준 그대로다`() {
        // given
        val candidate = keptOne(model("openai/gpt-oss-120b", paramsB = 116.8))

        // when
        val filled = ModelCandidateRules.withRepoFiles(candidate, gptOss120b())

        // then
        filled.fitsGpus.shouldContainExactly("pro6000")
    }

    private fun keptOne(model: HuggingFaceModel): CollectedModel = ModelCandidateRules.select(listOf(model)).kept.single()

    private fun trendingFixture(): List<HuggingFaceModel> {
        val body = ClassPathResource("modelcatalog/trending-2026-11-03.json").getContentAsByteArray()
        val builder = RestClient.builder().baseUrl("https://example.test/api")
        MockRestServiceServer.bindTo(builder).build()
            .expect(ExpectedCount.once(), anything())
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        return RestHuggingFaceClient(builder.build(), HuggingFaceProperties()).fetchPopular(50)
    }
}
