package com.duro.kukie.hosting.application

import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.application.port.out.HostingFailureAnalyzer
import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingIdentity
import com.duro.kukie.hosting.domain.HostingStartRetry
import com.duro.kukie.hosting.domain.HostingStartRetryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 빌드는 됐지만 시작하지 못한 자동 감지 빌드를, AI 가 로그와 파일 이름을 보고 고른 시작 명령으로 한 번 더 배포한다. 배포마다 한 번,
 * 재시도한 배포는 다시 재시도하지 않는다. 사용자가 kukie.json 에 적은 start 나 Dockerfile 로 빌드한 앱은 건드리지 않는다.
 * 서버는 배포를 따로 지켜보지 않아서, 화면이나 에이전트가 그 실패를 처음 읽을 때 시작한다.
 */
@Component
class HostingStartRetries internal constructor(
    private val hostingApps: HostingApps,
    private val hostingFailureAnalyzer: HostingFailureAnalyzer,
    private val hostingStartRetryRepository: HostingStartRetryRepository,
    private val hostingCommandQueue: HostingCommandQueue,
    private val hostingAgentRateLimit: HostingAgentRateLimit,
    private val objectMapper: ObjectMapper,
    private val executor: Executor,
) {

    @Autowired
    constructor(
        hostingApps: HostingApps,
        hostingFailureAnalyzer: HostingFailureAnalyzer,
        hostingStartRetryRepository: HostingStartRetryRepository,
        hostingCommandQueue: HostingCommandQueue,
        hostingAgentRateLimit: HostingAgentRateLimit,
        objectMapper: ObjectMapper,
    ) : this(
        hostingApps, hostingFailureAnalyzer, hostingStartRetryRepository, hostingCommandQueue, hostingAgentRateLimit, objectMapper,
        Executors.newVirtualThreadPerTaskExecutor(),
    )

    private val log = logger()

    /** 배포에 대해 이미 한 일. 없으면 null. */
    fun of(app: HostingApp, deployment: HostingDeployment): HostingStartRetry? =
        hostingStartRetryRepository.find(key(app, deployment))?.settled(Instant.now())

    /** 배포에 대해 한 일을 주고, 아직 아무것도 하지 않았고 재시도할 배포면 AI 에게 시작 명령을 고르게 한다(답을 기다리지 않는다). */
    fun follow(userId: UUID, app: HostingApp, deployment: HostingDeployment): HostingStartRetry? {
        of(app, deployment)?.let { return it }
        if (!retriable(app, deployment)) return null
        val analyzing = HostingStartRetry(HostingStartRetry.ANALYZING, now())
        val key = key(app, deployment)
        if (!hostingStartRetryRepository.claim(key, analyzing)) return of(app, deployment)
        if (!hostingAgentRateLimit.tryAcquire("start-retry:$userId", DAILY_LIMIT, Duration.ofDays(1))) {
            return HostingStartRetry(HostingStartRetry.SKIPPED, now(), reason = TOO_MANY).also { hostingStartRetryRepository.save(key, it) }
        }
        executor.execute { retry(key, app, deployment) }
        return analyzing
    }

    private fun retriable(app: HostingApp, deployment: HostingDeployment): Boolean {
        val build = deployment.build ?: return false
        return hostingFailureAnalyzer.enabled &&
            deployment.phase == FAILED && deployment.failure?.code == START_FAILED &&
            build.method == AUTO && deployment.config.start == null && deployment.retry == null &&
            app.latest.deploymentId == deployment.deploymentId &&
            (!app.uploaded || UPLOAD_ID.matches(deployment.commit.sha)) &&
            hostingApps.suspension(app) == null
    }

    private fun retry(key: String, app: HostingApp, deployment: HostingDeployment) {
        val result = runCatching {
            val logTail = hostingApps.buildLogTail(app, deployment.deploymentId, LOG_TAIL_BYTES).orEmpty()
            val answer = hostingFailureAnalyzer.analyze(HostingStartCommandPrompt.SYSTEM, HostingStartCommandPrompt.of(deployment, logTail), HostingFailureAnalyzer.Answer.START)
            val choice = parse(answer)
            val start = choice.start
            when {
                choice.reason == null -> skipped(NO_REASON)
                start == null || !choice.confident -> skipped(choice.reason)
                start == deployment.build?.start -> skipped(choice.reason)
                // 그사이 새 배포가 시작됐으면 옛 커밋(업로드)으로 되돌리지 않는다
                !stillLatest(app, deployment) -> skipped(SUPERSEDED)
                else -> {
                    val command = HostingCommand.retry(app, deployment, start)
                    hostingCommandQueue.send(command)
                    log.info("시작 명령 재시도를 보냈습니다: app={}/{}, deployment={}, command={}", app.team, app.app, deployment.deploymentId, command.commandId)
                    HostingStartRetry(HostingStartRetry.SENT, now(), start = start, reason = choice.reason, commandId = command.commandId)
                }
            }
        }.getOrElse {
            log.warn("시작 명령 재시도를 하지 못했습니다: app={}/{}, deployment={}, cause={}", app.team, app.app, deployment.deploymentId, it.message)
            skipped(NO_ANSWER)
        }
        hostingStartRetryRepository.save(key, result)
    }

    private fun stillLatest(app: HostingApp, deployment: HostingDeployment): Boolean =
        hostingApps.appOf(HostingIdentity(app.owner.id, app.team), app.app)
            ?.let { it.generation == app.generation && it.latest.deploymentId == deployment.deploymentId } == true

    private fun skipped(reason: String) = HostingStartRetry(HostingStartRetry.SKIPPED, now(), reason = reason)

    private data class Choice(val start: String?, val confident: Boolean, val reason: String?)

    private fun parse(answer: String): Choice {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        val node = if (start >= 0 && end > start) runCatching { objectMapper.readTree(answer.substring(start, end + 1)) as? ObjectNode }.getOrNull() else null
        node ?: return Choice(null, false, null)
        val command = node.get("start")?.takeIf { it.isString }?.asString()?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_START && it.none { c -> c == '\n' || c == '\r' || c == '\u0000' } }
        val confident = node.get("confident")?.takeIf { it.isBoolean }?.asBoolean() == true
        val reason = node.get("reason")?.takeIf { it.isString }?.asString()?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_REASON)
        return Choice(command, confident, reason)
    }

    private fun key(app: HostingApp, deployment: HostingDeployment) = "${app.team}:${app.app}:${app.generation}:${deployment.deploymentId}"

    private fun now() = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    companion object {
        private const val FAILED = "failed"
        private const val START_FAILED = "start_failed"
        private const val AUTO = "auto"
        private const val DAILY_LIMIT = 5
        private const val LOG_TAIL_BYTES = 12_000

        // 배포기의 kukie.json start 와 같은 한도
        private const val MAX_START = 500
        private const val MAX_REASON = 1_000
        private val UPLOAD_ID = Regex("[0-9a-f]{40}")

        const val NO_ANSWER = HostingStartRetry.NO_ANSWER
        const val NO_REASON = "AI 가 시작 명령을 고르지 못했어요."
        const val SUPERSEDED = "그사이 새 배포가 시작돼 다시 배포하지 않았어요."
        const val TOO_MANY = "오늘 자동 재시도를 다 써서 다시 배포하지 않았어요."
    }
}
