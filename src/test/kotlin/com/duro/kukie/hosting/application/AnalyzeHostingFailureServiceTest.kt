package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.hosting.application.port.out.HostingAnalysisCache
import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer
import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingCommit
import com.duro.kukie.hosting.domain.HostingConfig
import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingFailure
import com.duro.kukie.hosting.exception.HostingAnalysisDisabledException
import com.duro.kukie.hosting.exception.HostingTooManyAnalysesException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

class AnalyzeHostingFailureServiceTest {

    private val hostingApps = mockk<HostingApps>()
    private val analyzer = mockk<HostingFailureAnalyzer>()
    private val cache = mockk<HostingAnalysisCache>()
    private val rateLimit = mockk<HostingAgentRateLimit>()
    private val service = AnalyzeHostingFailureService(hostingApps, analyzer, cache, rateLimit, JsonMapper.builder().build())

    private val userId = UUID.randomUUID()
    private val app = mockk<HostingApp>(relaxed = true)

    @Test
    fun `모델이 JSON 앞뒤에 글을 붙여도 원인과 고칠 방법을 읽는다`() {
        // given
        failedDeployment()
        every { analyzer.analyze(any(), any()) } returns "분석입니다.\n{\"cause\": \"포트가 다릅니다.\", \"fixes\": [\"PORT 를 쓰세요.\", 3]}\n끝"

        // when
        val result = service(userId, "shop", DEPLOYMENT)

        // then
        result.cause shouldBe "포트가 다릅니다."
        result.fixes shouldBe listOf("PORT 를 쓰세요.")
        result.cached shouldBe false
        verify { cache.put(any(), any()) }
    }

    @Test
    fun `JSON 이 아닌 답은 그대로 원인으로 준다`() {
        // given
        failedDeployment()
        every { analyzer.analyze(any(), any()) } returns "  로그만으로는 알 수 없습니다.  "

        // when
        val result = service(userId, "shop", DEPLOYMENT)

        // then
        result.cause shouldBe "로그만으로는 알 수 없습니다."
        result.fixes.shouldBeEmpty()
    }

    @Test
    fun `하루 횟수를 넘으면 모델을 부르지 않는다`() {
        // given
        failedDeployment()
        every { rateLimit.tryAcquire("analysis:$userId", 10, any()) } returns false

        // when & then
        shouldThrow<HostingTooManyAnalysesException> { service(userId, "shop", DEPLOYMENT) }
        verify(exactly = 0) { analyzer.analyze(any(), any()) }
    }

    @Test
    fun `키가 없는 서버에서는 앱을 읽지도 않는다`() {
        // given
        every { analyzer.enabled } returns false

        // when & then
        shouldThrow<HostingAnalysisDisabledException> { service(userId, "shop", DEPLOYMENT) }
        verify(exactly = 0) { hostingApps.requireOwned(any(), any()) }
    }

    private fun failedDeployment() {
        every { analyzer.enabled } returns true
        every { hostingApps.requireOwned(userId, "shop") } returns app
        every { hostingApps.deployment(app, DEPLOYMENT) } returns HostingDeployment(
            version = 1,
            deploymentId = DEPLOYMENT,
            phase = "failed",
            trigger = "deploy",
            commit = HostingCommit("0123456"),
            config = HostingConfig(port = 3000, size = "S", source = "default"),
            imageReused = false,
            hasLog = true,
            failure = HostingFailure("start_failed", "app exited"),
            startedAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        every { hostingApps.buildLogTail(app, DEPLOYMENT, any()) } returns "=== app ===\nlistening on 127.0.0.1:5000"
        every { cache.get(any()) } returns null
        justRun { cache.put(any(), any()) }
        every { rateLimit.tryAcquire(any(), any(), any()) } returns true
    }

    companion object {
        private const val DEPLOYMENT = "20261109T000000Z-aaaaaaa"
    }
}
