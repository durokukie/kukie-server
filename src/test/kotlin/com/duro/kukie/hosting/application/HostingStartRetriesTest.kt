package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer
import com.duro.kukie.hosting.domain.HostingAccount
import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingBuild
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.domain.HostingCommit
import com.duro.kukie.hosting.domain.HostingConfig
import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingFailure
import com.duro.kukie.hosting.domain.HostingLatest
import com.duro.kukie.hosting.domain.HostingRepo
import com.duro.kukie.hosting.domain.HostingRetryRef
import com.duro.kukie.hosting.domain.HostingSource
import com.duro.kukie.hosting.domain.HostingStartRetry
import com.duro.kukie.hosting.domain.HostingStartRetryRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

class HostingStartRetriesTest {

    private val hostingApps = mockk<HostingApps>()
    private val analyzer = mockk<HostingFailureAnalyzer>()
    private val repository = InMemoryRetries()
    private val queue = mockk<HostingCommandQueue>()
    private val rateLimit = mockk<HostingAgentRateLimit>()
    private val retries = HostingStartRetries(hostingApps, analyzer, repository, queue, rateLimit, JsonMapper.builder().build()) { it.run() }

    private val userId = UUID.randomUUID()
    private val sent = slot<HostingCommand>()

    @BeforeEach
    fun setUp() {
        every { analyzer.enabled } returns true
        every { rateLimit.tryAcquire(any(), any(), any()) } returns true
        every { hostingApps.buildLogTail(any(), any(), any()) } returns "=== app ===\nsh: go-getting-started: not found"
        justRun { queue.send(capture(sent)) }
    }

    @Test
    fun `AI 가 확신하는 시작 명령이면 그 명령으로 재배포를 보낸다`() {
        // given
        val app = app()
        every { hostingApps.appOf(any(), "shop") } returns app
        every { analyzer.analyze(any(), any(), HostingFailureAnalyzer.Answer.START) } returns
            """{"start": "./out", "confident": true, "reason": "Railpack 의 Go 빌드는 ./out 을 만듭니다."}"""

        // when
        val result = retries.follow(userId, app, failed())

        // then
        result?.status shouldBe HostingStartRetry.ANALYZING
        val saved = retries.of(app, failed())
        saved?.status shouldBe HostingStartRetry.SENT
        saved?.start shouldBe "./out"
        sent.captured.type shouldBe HostingCommand.REDEPLOY
        sent.captured.start shouldBe "./out"
        sent.captured.retryOf shouldBe DEPLOYMENT
        saved?.commandId shouldBe sent.captured.commandId
    }

    @Test
    fun `업로드 앱은 그 배포의 업로드를 다시 보낸다`() {
        // given
        val app = app(uploaded = true)
        every { hostingApps.appOf(any(), "shop") } returns app
        every { analyzer.analyze(any(), any(), HostingFailureAnalyzer.Answer.START) } returns """{"start": "./out", "confident": true, "reason": "r"}"""

        // when
        retries.follow(userId, app, failed())

        // then
        sent.captured.type shouldBe HostingCommand.UPLOAD
        sent.captured.source?.key shouldBe "uploads/recklehs/shop/$UPLOAD_ID.tar.gz"
        sent.captured.source?.sha shouldBe UPLOAD_ID
        sent.captured.generation.shouldBeNull()
    }

    @Test
    fun `같은 배포는 한 번만 재시도한다`() {
        // given
        val app = app()
        every { hostingApps.appOf(any(), "shop") } returns app
        every { analyzer.analyze(any(), any(), any()) } returns """{"start": "./out", "confident": true, "reason": "r"}"""

        // when
        retries.follow(userId, app, failed())
        val again = retries.follow(userId, app, failed())

        // then
        again?.status shouldBe HostingStartRetry.SENT
        verify(exactly = 1) { queue.send(any()) }
    }

    @Test
    fun `AI 가 확신하지 않으면 재배포하지 않고 까닭을 남긴다`() {
        // given
        val app = app()
        every { analyzer.analyze(any(), any(), any()) } returns """{"start": "", "confident": false, "reason": "ImportError 라 코드를 고쳐야 합니다."}"""

        // when
        retries.follow(userId, app, failed())

        // then
        val saved = retries.of(app, failed())
        saved?.status shouldBe HostingStartRetry.SKIPPED
        saved?.reason shouldBe "ImportError 라 코드를 고쳐야 합니다."
        verify(exactly = 0) { queue.send(any()) }
    }

    @Test
    fun `지금 명령과 같은 명령이나 여러 줄 명령은 보내지 않는다`() {
        // given
        val app = app()
        every { analyzer.analyze(any(), any(), any()) } returnsMany listOf(
            """{"start": "go-getting-started", "confident": true, "reason": "r"}""",
            """{"start": "./out\nrm -rf /app", "confident": true, "reason": "r"}""",
        )

        val later = app(latest = OTHER)

        // when
        retries.follow(userId, app, failed())
        retries.follow(userId, later, failed(id = OTHER))

        // then
        retries.of(app, failed())?.status shouldBe HostingStartRetry.SKIPPED
        retries.of(later, failed(id = OTHER))?.status shouldBe HostingStartRetry.SKIPPED
        verify(exactly = 0) { queue.send(any()) }
    }

    @Test
    fun `그사이 새 배포가 시작됐으면 재배포하지 않는다`() {
        // given
        val app = app()
        every { hostingApps.appOf(any(), "shop") } returns app(latest = OTHER)
        every { analyzer.analyze(any(), any(), any()) } returns """{"start": "./out", "confident": true, "reason": "r"}"""

        // when
        retries.follow(userId, app, failed())

        // then
        retries.of(app, failed())?.reason shouldBe HostingStartRetries.SUPERSEDED
        verify(exactly = 0) { queue.send(any()) }
    }

    @Test
    fun `kukie_json 의 start·Dockerfile·재시도한 배포·다른 실패는 재시도하지 않는다`() {
        // given
        val app = app()
        val cases = listOf(
            failed().copy(config = HostingConfig(port = 3000, size = "S", source = "file", start = "go-getting-started")),
            failed().copy(build = HostingBuild(method = "dockerfile")),
            failed().copy(retry = HostingRetryRef(OTHER, "./out")),
            failed().copy(failure = HostingFailure("build_failed", "x")),
        )

        // when
        val results = cases.map { retries.follow(userId, app, it) }

        // then
        results shouldBe listOf(null, null, null, null)
        verify(exactly = 0) { analyzer.analyze(any(), any(), any()) }
    }

    @Test
    fun `하루 횟수를 넘으면 AI 를 부르지 않고 까닭을 남긴다`() {
        // given
        val app = app()
        every { rateLimit.tryAcquire("start-retry:$userId", 5, any()) } returns false

        // when
        val result = retries.follow(userId, app, failed())

        // then
        result?.status shouldBe HostingStartRetry.SKIPPED
        result?.reason shouldBe HostingStartRetries.TOO_MANY
        verify(exactly = 0) { analyzer.analyze(any(), any(), any()) }
    }

    @Test
    fun `AI 를 부르지 못하면 재배포하지 않는다`() {
        // given
        val app = app()
        every { analyzer.analyze(any(), any(), any()) } throws RuntimeException("timeout")

        // when
        retries.follow(userId, app, failed())

        // then
        retries.of(app, failed())?.reason shouldBe HostingStartRetries.NO_ANSWER
    }

    private fun app(uploaded: Boolean = false, latest: String = DEPLOYMENT) = HostingApp(
        version = 1,
        owner = HostingAccount(127021195, "Recklehs"),
        repo = if (uploaded) null else HostingRepo(fullName = "Recklehs/shop"),
        source = if (uploaded) HostingSource(HostingSource.UPLOAD) else null,
        team = "recklehs",
        app = "shop",
        generation = "20261101T031500Z",
        url = "https://shop--recklehs.example",
        config = HostingConfig(port = 3000, size = "S", source = "default"),
        latest = HostingLatest(deploymentId = latest, phase = "failed", commit = HostingCommit(UPLOAD_ID), at = Instant.EPOCH),
    )

    private fun failed(id: String = DEPLOYMENT) = HostingDeployment(
        version = 1,
        deploymentId = id,
        phase = "failed",
        trigger = "push",
        commit = HostingCommit(UPLOAD_ID),
        config = HostingConfig(port = 3000, size = "S", source = "default"),
        build = HostingBuild(method = "auto", providers = listOf("golang"), start = "go-getting-started", files = listOf("go.mod", "main.go", "Procfile")),
        imageReused = false,
        hasLog = true,
        failure = HostingFailure("start_failed", "the start command could not be run"),
        startedAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private class InMemoryRetries : HostingStartRetryRepository {
        val saved = mutableMapOf<String, HostingStartRetry>()

        override fun claim(key: String, retry: HostingStartRetry) = saved.putIfAbsent(key, retry) == null

        override fun find(key: String) = saved[key]

        override fun save(key: String, retry: HostingStartRetry) {
            saved[key] = retry
        }
    }

    companion object {
        private const val DEPLOYMENT = "20261120T010203Z-0123abc"
        private const val OTHER = "20261120T020203Z-0123abc"
        private const val UPLOAD_ID = "0123abc456789def0123abc456789def01234567"
    }
}
