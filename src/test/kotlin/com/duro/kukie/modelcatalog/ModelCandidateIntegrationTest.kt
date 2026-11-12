package com.duro.kukie.modelcatalog

import com.duro.kukie.modelcatalog.ModelCatalogFixture.gptOss120b
import com.duro.kukie.modelcatalog.ModelCatalogFixture.gptOss20b
import com.duro.kukie.modelcatalog.ModelCatalogFixture.model
import com.duro.kukie.modelcatalog.domain.GatedAccess
import com.duro.kukie.modelcatalog.domain.ModelCandidate
import com.duro.kukie.modelcatalog.domain.ModelCandidateRepository
import com.duro.kukie.modelcatalog.domain.ModelCandidateRules
import com.duro.kukie.modelcatalog.domain.ModelFavorite
import com.duro.kukie.modelcatalog.domain.ModelFavoriteRepository
import com.duro.kukie.cluster.ClusterFixture
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.modelcatalog.exception.ModelCatalogErrorCode
import com.duro.kukie.support.FakeHuggingFaceClient
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.support.LoggedInUser
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class ModelCandidateIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var fakeHuggingFaceClient: FakeHuggingFaceClient

    @Autowired
    private lateinit var modelCandidateRepository: ModelCandidateRepository

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var modelFavoriteRepository: ModelFavoriteRepository

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @BeforeEach
    fun verifiedModelOffTheList() {
        fakeHuggingFaceClient.models["openai/gpt-oss-120b"] = gptOss120b()
    }

    @Test
    fun `갱신하면 허깅페이스 후보를 거르고 저장해 약속한 형태로 준다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(
            model("OBLITERATUS/gpt-oss-20b-obliterated"),
            model("Qwen/Qwen3-32B", paramsB = 32.8, usedStorageGb = 66.0, weightGb = 66.0, license = "llama3.1", downloads = 500),
            model("KRAFTON/Raon-1-mini-preview", paramsB = 28.8, architecture = "Raon1MiniForCausalLM", gated = GatedAccess.AUTO, downloads = 10),
            gptOss120b().copy(downloads = 900),
            gptOss20b().copy(downloads = 800),
        )

        // when & then
        refresh(user.accessToken).andExpect {
            status { isOk() }
            jsonPath("$.generatedAt") { exists() }
            jsonPath("$.candidates.length()") { value(4) }
            jsonPath("$.candidates[0].id") { value("openai/gpt-oss-120b") }
            jsonPath("$.candidates[0].weightGb") { value(65.2) }
            jsonPath("$.candidates[0].gpus.length()") { value(1) }
            jsonPath("$.candidates[0].gpus[0]") { value("pro6000") }
            jsonPath("$.candidates[0].verified") { value(true) }
            jsonPath("$.candidates[0].maxConcurrency") { value(8) }
            jsonPath("$.candidates[0].measuredGpu") { value("l40s") }
            jsonPath("$.candidates[0].measuredGpus") { value(4) }
            jsonPath("$.candidates[1].id") { value("openai/gpt-oss-20b") }
            jsonPath("$.candidates[1].paramsB") { value(20.9) }
            jsonPath("$.candidates[1].sizeGb") { value(41.4) }
            jsonPath("$.candidates[1].weightGb") { value(13.8) }
            jsonPath("$.candidates[1].license") { value("apache-2.0") }
            jsonPath("$.candidates[1].gated") { value(false) }
            jsonPath("$.candidates[1].vllmNative") { value(true) }
            jsonPath("$.candidates[1].gpus[0]") { value("l40s") }
            jsonPath("$.candidates[1].gpus[1]") { value("pro6000") }
            jsonPath("$.candidates[1].verified") { value(true) }
            jsonPath("$.candidates[1].maxConcurrency") { value(32) }
            jsonPath("$.candidates[1].measuredGpu") { value("l40s") }
            jsonPath("$.candidates[1].measuredGpus") { value(1) }
            jsonPath("$.candidates[2].id") { value("Qwen/Qwen3-32B") }
            jsonPath("$.candidates[2].licenseNote") { value("조건 있음") }
            jsonPath("$.candidates[2].gpus.length()") { value(1) }
            jsonPath("$.candidates[2].gpus[0]") { value("pro6000") }
            jsonPath("$.candidates[2].verified") { value(false) }
            jsonPath("$.candidates[2].maxConcurrency") { value(null) }
            jsonPath("$.candidates[2].measuredGpu") { value(null) }
            jsonPath("$.candidates[2].measuredGpus") { value(null) }
            jsonPath("$.candidates[3].id") { value("KRAFTON/Raon-1-mini-preview") }
            jsonPath("$.candidates[3].gated") { value(true) }
            jsonPath("$.candidates[3].vllmNative") { value(false) }
            jsonPath("$.candidates[3].sizeGb") { value(null) }
            jsonPath("$.candidates[3].weightGb") { value(null) }
            jsonPath("$.candidates[3].gpus") { isEmpty() }
        }
    }

    @Test
    fun `저장된 목록을 읽을 때는 허깅페이스를 부르지 않는다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b(), model("Qwen/Qwen3-32B"))
        refresh(user.accessToken)
        val calls = fakeHuggingFaceClient.calls

        // when & then
        mockMvc.get("/gpu/model-candidates") {
            authorization(user.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.generatedAt") { exists() }
            jsonPath("$.candidates[0].id") { value("openai/gpt-oss-20b") }
            jsonPath("$.candidates[0].verified") { value(true) }
            jsonPath("$.candidates[1].verified") { value(true) }
            jsonPath("$.candidates[2].verified") { value(false) }
        }
        fakeHuggingFaceClient.calls shouldBe calls
    }

    @Test
    fun `갱신은 원작자 회사마다 다운로드 순 목록을 50개씩 받는다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b())

        // when
        refresh(user.accessToken)

        // then
        fakeHuggingFaceClient.authors shouldContainExactly ModelCandidateRules.AUTHOR_ACCOUNTS
        fakeHuggingFaceClient.lastLimit shouldBe 50
    }

    @Test
    fun `한 회사 모델은 3개까지만 남기고 검증·즐겨찾기 모델은 따로 남긴다`() {
        // given
        val user = teamAdmin()
        favorite(user, "Qwen/q-favorite")
        fakeHuggingFaceClient.popular = (1..5).map { model("Qwen/q-$it", downloads = 100L - it) } +
            model("Qwen/q-favorite", downloads = 1) + model("upstage/solar", downloads = 50) + gptOss20b()

        // when
        refresh(user.accessToken).andExpect { status { isOk() } }

        // then
        modelCandidateRepository.findAllByOrderByDisplayOrder().map { it.modelId }.shouldContainExactly(
            "Qwen/q-1", "upstage/solar", "openai/gpt-oss-20b",
            "Qwen/q-2", "openai/gpt-oss-120b",
            "Qwen/q-3",
            "Qwen/q-favorite",
        )
    }

    @Test
    fun `다운로드 순 목록에 없는 즐겨찾기 모델도 따로 받아 후보에 남긴다`() {
        // given
        val user = teamAdmin()
        favorite(user, "Qwen/Qwen3-32B")
        fakeHuggingFaceClient.popular = listOf(gptOss20b())
        fakeHuggingFaceClient.models["Qwen/Qwen3-32B"] = model("Qwen/Qwen3-32B")

        // when
        refresh(user.accessToken).andExpect { status { isOk() } }

        // then
        modelCandidateRepository.findAll().map { it.modelId }.toSet() shouldBe
            setOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "Qwen/Qwen3-32B")
    }

    @Test
    fun `순위 밖 즐겨찾기는 30개까지만 따로 받는다`() {
        // given
        val user = teamAdmin()
        (1..31).forEach { favorite(user, "gone/model-%02d".format(it)) }
        fakeHuggingFaceClient.popular = listOf(gptOss20b())

        // when
        refresh(user.accessToken).andExpect { status { isOk() } }

        // then
        fakeHuggingFaceClient.fetched.count { it.startsWith("gone/") } shouldBe 30
    }

    @Test
    fun `허깅페이스에서 사라진 즐겨찾기 모델은 빼고 갱신은 성공한다`() {
        // given
        val user = teamAdmin()
        favorite(user, "gone/model-70B")
        fakeHuggingFaceClient.popular = listOf(gptOss20b())

        // when & then
        refresh(user.accessToken).andExpect { status { isOk() } }
        modelCandidateRepository.findAll().map { it.modelId }.toSet() shouldBe setOf("openai/gpt-oss-20b", "openai/gpt-oss-120b")
    }

    @Test
    fun `아직 갱신한 적이 없으면 빈 목록을 준다`() {
        // given
        val user = loggedInUser()

        // when & then
        mockMvc.get("/gpu/model-candidates") {
            authorization(user.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.generatedAt") { value(null) }
            jsonPath("$.candidates") { isEmpty() }
        }
        fakeHuggingFaceClient.calls shouldBe 0
    }

    @Test
    fun `다운로드 순 목록에서 빠진 검증 모델은 따로 받아 후보에 남긴다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(model("Qwen/Qwen3-32B"))
        fakeHuggingFaceClient.models["openai/gpt-oss-20b"] = gptOss20b()

        // when & then
        refresh(user.accessToken).andExpect {
            status { isOk() }
            jsonPath("$.candidates[0].id") { value("openai/gpt-oss-20b") }
            jsonPath("$.candidates[0].verified") { value(true) }
            jsonPath("$.candidates[1].id") { value("openai/gpt-oss-120b") }
            jsonPath("$.candidates[1].verified") { value(true) }
            jsonPath("$.candidates[2].id") { value("Qwen/Qwen3-32B") }
        }
    }

    @Test
    fun `여러 장에서 잰 검증 모델은 GPU 1장에 안 들어가도 실측한 장수에 들어가면 갱신한다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b())

        // when & then
        refresh(user.accessToken).andExpect {
            status { isOk() }
            jsonPath("$.candidates[0].id") { value("openai/gpt-oss-20b") }
            jsonPath("$.candidates[1].id") { value("openai/gpt-oss-120b") }
            jsonPath("$.candidates[1].verified") { value(true) }
        }
    }

    @Test
    fun `다시 갱신하면 덮어쓰고 빠진 모델을 지우며 이전 수집 시각을 남긴다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b(), model("Qwen/Qwen3-32B"))
        refresh(user.accessToken)
        val firstCollectedAt = saved("openai/gpt-oss-20b").collectedAt
        fakeHuggingFaceClient.popular = listOf(model("google/g-30B", paramsB = 30.0, downloads = 2), gptOss20b())
        redisTemplate.delete(REFRESH_LOCK_KEY)

        // when
        refresh(user.accessToken).andExpect { status { isOk() } }

        // then
        modelCandidateRepository.findAllByOrderByDisplayOrder().map { it.modelId }
            .shouldContainExactly("google/g-30B", "openai/gpt-oss-20b", "openai/gpt-oss-120b")
        saved("openai/gpt-oss-20b").run {
            previousCollectedAt shouldBe firstCollectedAt
            collectedAt.isAfter(firstCollectedAt) shouldBe true
        }
        saved("google/g-30B").previousCollectedAt.shouldBeNull()
    }

    @Test
    fun `크기를 읽지 못한 후보가 있으면 저장하지 않는다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b(), model("Qwen/Qwen3-32B"))
        fakeHuggingFaceClient.missing += "Qwen/Qwen3-32B"

        // when & then
        refresh(user.accessToken).andExpect { status { isBadGateway() } }
        modelCandidateRepository.count() shouldBe 0
    }

    @Test
    fun `허깅페이스를 읽지 못하면 502 를 주고 저장된 목록을 그대로 둔다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b())
        refresh(user.accessToken)
        val collectedAt = saved("openai/gpt-oss-20b").collectedAt
        fakeHuggingFaceClient.failing = true
        redisTemplate.delete(REFRESH_LOCK_KEY)

        // when & then
        refresh(user.accessToken).andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value(ModelCatalogErrorCode.MODEL_CANDIDATES_REFRESH_FAILED.code) }
        }
        saved("openai/gpt-oss-20b").collectedAt shouldBe collectedAt
    }

    @Test
    fun `빈 목록이 오면 저장된 목록을 지우지 않는다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b())
        refresh(user.accessToken)
        fakeHuggingFaceClient.popular = emptyList()
        redisTemplate.delete(REFRESH_LOCK_KEY)

        // when & then
        refresh(user.accessToken).andExpect { status { isBadGateway() } }
        saved("openai/gpt-oss-20b").shouldNotBeNull()
    }

    @Test
    fun `검증된 모델이 규칙에 걸리면 갱신하지 않는다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(model("openai/gpt-oss-20b", license = "cc-by-nc-4.0"))

        // when & then
        refresh(user.accessToken).andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value(ModelCatalogErrorCode.MODEL_CANDIDATES_REFRESH_FAILED.code) }
        }
        modelCandidateRepository.count() shouldBe 0
    }

    @Test
    fun `검증된 모델이 실측한 GPU 에 안 들어간다고 나오면 갱신하지 않는다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(model("openai/gpt-oss-20b", weightGb = 40.0))

        // when & then
        refresh(user.accessToken).andExpect { status { isBadGateway() } }
        modelCandidateRepository.count() shouldBe 0
    }

    @Test
    fun `여러 장에서 잰 검증 모델이 실측한 장수에도 안 들어가면 갱신하지 않는다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b())
        fakeHuggingFaceClient.models["openai/gpt-oss-120b"] = model("openai/gpt-oss-120b", paramsB = 116.8, weightGb = 141.0)

        // when & then
        refresh(user.accessToken).andExpect { status { isBadGateway() } }
        modelCandidateRepository.count() shouldBe 0
    }

    @Test
    fun `이름이 바뀐 검증 모델은 갱신하지 않는다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(model("Qwen/Qwen3-32B"))
        fakeHuggingFaceClient.models["openai/gpt-oss-20b"] = model("openai/renamed")

        // when & then
        refresh(user.accessToken).andExpect { status { isBadGateway() } }
        modelCandidateRepository.count() shouldBe 0
    }

    @Test
    fun `방금 갱신했으면 허깅페이스를 다시 부르지 않고 429 를 준다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.popular = listOf(gptOss20b())
        refresh(user.accessToken)
        val calls = fakeHuggingFaceClient.calls

        // when & then
        refresh(user.accessToken).andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value(ModelCatalogErrorCode.MODEL_CANDIDATES_REFRESH_TOO_SOON.code) }
        }
        fakeHuggingFaceClient.calls shouldBe calls
    }

    @Test
    fun `팀 관리자가 아니면 허깅페이스를 부르지 않고 갱신을 거부한다`() {
        // given
        val member = loggedInUser()
        savedTeam(member.user.id to TeamRole.MEMBER)
        val noTeam = loggedInUser(UserFixture.user(email = "no-team@example.com"))

        listOf(member, noTeam).forEach { user ->
            // when & then
            refresh(user.accessToken).andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
            }
        }
        fakeHuggingFaceClient.calls shouldBe 0
    }

    @Test
    fun `갱신에 실패하면 바로 다시 시도할 수 있다`() {
        // given
        val user = teamAdmin()
        fakeHuggingFaceClient.failing = true
        refresh(user.accessToken).andExpect { status { isBadGateway() } }
        fakeHuggingFaceClient.failing = false
        fakeHuggingFaceClient.popular = listOf(gptOss20b())

        // when & then
        refresh(user.accessToken).andExpect {
            status { isOk() }
            jsonPath("$.candidates[0].id") { value("openai/gpt-oss-20b") }
        }
    }

    @Test
    fun `갱신이 도는 동안에는 잠금을 쥐고 성공한 뒤부터 60초를 센다`() {
        // given
        val user = teamAdmin()
        var remainingDuringCall = 0L
        var secondRefreshStatus = 0
        fakeHuggingFaceClient.popular = listOf(gptOss20b())
        fakeHuggingFaceClient.onCall = {
            fakeHuggingFaceClient.onCall = {}
            remainingDuringCall = redisTemplate.getExpire(REFRESH_LOCK_KEY)
            secondRefreshStatus = refresh(user.accessToken).andReturn().response.status
        }

        // when
        refresh(user.accessToken).andExpect { status { isOk() } }

        // then
        secondRefreshStatus shouldBe 429
        remainingDuringCall shouldBeGreaterThan 60
        redisTemplate.getExpire(REFRESH_LOCK_KEY).let {
            it shouldBeGreaterThan 0
            it shouldBeLessThanOrEqual 60
        }
    }

    @Test
    fun `로그인하지 않으면 조회도 갱신도 할 수 없다`() {
        // when & then
        mockMvc.get("/gpu/model-candidates").andExpect { status { isUnauthorized() } }
        mockMvc.post("/gpu/model-candidates/refresh").andExpect { status { isUnauthorized() } }
        fakeHuggingFaceClient.calls shouldBe 0
    }

    private fun teamAdmin(): LoggedInUser = loggedInUser().also { savedTeam(it.user.id to TeamRole.ADMIN) }

    private fun refresh(accessToken: String): ResultActionsDsl =
        mockMvc.post("/gpu/model-candidates/refresh") { authorization(accessToken) }

    private fun favorite(user: LoggedInUser, modelId: String) {
        val teamId = savedTeam(user.user.id to TeamRole.ADMIN)
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = user.user.id, teamId = teamId))
        modelFavoriteRepository.save(ModelFavorite(cluster.id, modelId, user.user.id))
    }

    private fun saved(modelId: String): ModelCandidate =
        modelCandidateRepository.findAll().single { it.modelId == modelId }

    companion object {
        private const val REFRESH_LOCK_KEY = "model-candidate:refresh-lock"
    }
}
