package com.duro.kukie.hosting.application

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.config.properties.WebAppProperties
import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.hosting.domain.HostingAgentDeploy
import com.duro.kukie.hosting.domain.HostingAgentDeployRepository
import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingFailure
import com.duro.kukie.hosting.domain.HostingIdentity
import com.duro.kukie.hosting.domain.HostingStartRetry
import com.duro.kukie.hosting.exception.HostingDeployNotStartedException
import com.duro.kukie.hosting.exception.HostingTooManyRequestsException
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentDeployStatusResponse
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class WaitHostingAgentDeployService(
    private val hostingApps: HostingApps,
    private val hostingAgentDeploys: HostingAgentDeploys,
    private val hostingAgentDeployRepository: HostingAgentDeployRepository,
    private val hostingAgentRateLimit: HostingAgentRateLimit,
    private val hostingLongPolls: HostingLongPolls,
    private val hostingProperties: HostingProperties,
    private val webAppProperties: WebAppProperties,
    private val hostingStartRetries: HostingStartRetries,
) {

    /** 배포가 끝나거나 timeout(최대 agentDeployWait)이 지날 때까지 기다려 상태를 준다. 에이전트는 done 이 true 가 될 때까지 다시 부른다. */
    operator fun invoke(userId: UUID, id: String, timeoutSeconds: Long?): HostingAgentDeployStatusResponse {
        if (!hostingAgentRateLimit.tryAcquire("wait:$userId", WAITS_PER_MINUTE, Duration.ofMinutes(1))) throw HostingTooManyRequestsException()
        val max = hostingProperties.agentDeployWait
        val wait = timeoutSeconds?.let { Duration.ofSeconds(it.coerceIn(0, max.seconds)) } ?: max
        return hostingLongPolls.hold { await(userId, id, Instant.now().plus(wait)) }
    }

    /**
     * 계정의 진행 중 표시가 가리키는 배포가 끝났으면 표시를 치운다. 에이전트가 결과를 받기 전에 끊겨도 그 배포가 다음 배포를 막지
     * 않는다. 아직 시작 중인 배포(commandId 없음)는 건드리지 않는다.
     */
    fun settleActive(userId: UUID, team: String) {
        val id = hostingAgentDeploys.activeOf(userId, team) ?: return
        val deploy = hostingAgentDeployRepository.find(id) ?: return
        if (deploy.commandId != null && status(deploy).done) hostingAgentDeployRepository.clearActive(team, id)
    }

    private fun await(userId: UUID, id: String, deadline: Instant): HostingAgentDeployStatusResponse {
        while (true) {
            val deploy = hostingAgentDeploys.requireOwned(userId, id)
            if (deploy.commandId == null) throw HostingDeployNotStartedException()
            val status = status(deploy)
            if (status.done) hostingAgentDeployRepository.clearActive(deploy.team, deploy.id)
            if (status.done || Duration.between(Instant.now(), deadline) < CHECK_INTERVAL) return status
            Thread.sleep(CHECK_INTERVAL.toMillis())
        }
    }

    /**
     * 앱 파일의 latest.commandId 가 이 명령인 동안은 그 배포를 따라간다(배포기가 재시작해 같은 명령을 다시 하면 배포가 새로 생긴다).
     * latest 가 다른 배포로 바뀐 뒤에는 마지막으로 본 배포의 기록을 읽는다. 배포기가 거절했으면 _skipped 의 commandId 가 이 명령이다.
     */
    private fun status(start: HostingAgentDeploy): HostingAgentDeployStatusResponse {
        var deploy = start
        val account = HostingIdentity(deploy.ownerId, deploy.login)
        val elapsed = Duration.between(deploy.startedAt ?: deploy.createdAt, Instant.now()).seconds
        val app = hostingApps.appOf(account, deploy.app)
        if (app != null && app.latest.commandId == deploy.commandId && app.latest.deploymentId != deploy.deploymentId) {
            deploy = deploy.copy(deploymentId = app.latest.deploymentId, generation = app.generation)
            hostingAgentDeploys.save(deploy)
        }
        // 에이전트가 다시 부르기 전에 다른 배포(레포 연결 뒤 push 등)가 latest 를 덮었으면 배포 기록에서 이 명령의 배포를 찾는다.
        // latest 가 시작보다 앞이면(배포기가 아직 받지 않음) 찾지 않는다. 찾지 못해도 이 명령이 아직 처리되지 않은 것이라 나중에
        // latest 가 되어 위에서 잡히므로, 목록 읽기는 배포마다 SEARCH_INTERVAL 에 한 번으로 줄인다
        val startedAt = deploy.startedAt
        if (app != null && deploy.deploymentId == null && app.latest.commandId != deploy.commandId && startedAt != null && app.latest.at > startedAt &&
            hostingAgentRateLimit.tryAcquire("search:${deploy.id}", 1, SEARCH_INTERVAL)
        ) {
            hostingApps.deployments(app).firstOrNull { it.commandId == deploy.commandId }?.let {
                deploy = deploy.copy(deploymentId = it.deploymentId, generation = app.generation)
                hostingAgentDeploys.save(deploy)
            }
        }
        val deploymentId = deploy.deploymentId
        if (deploymentId != null) {
            if (app == null || app.generation != deploy.generation) return failed(elapsed, HostingFailure(APP_DELETED, "the app was deleted during the deployment"))
            return deploymentStatus(deploy, app, deploymentId, elapsed)
        }
        val skipped = hostingApps.lastSkipped(account)
        if (skipped != null && skipped.commandId == deploy.commandId) return failed(elapsed, HostingFailure(skipped.reason, "the deployer refused the upload"))
        if (elapsed > QUEUED_LIMIT.seconds) return failed(elapsed, HostingFailure(STUCK, "the deployer has not taken the upload"))
        return HostingAgentDeployStatusResponse(done = false, status = QUEUED, elapsed = elapsed)
    }

    private fun deploymentStatus(deploy: HostingAgentDeploy, app: HostingApp, deploymentId: String, elapsed: Long): HostingAgentDeployStatusResponse {
        val record = hostingApps.deployment(app, deploymentId)
        val phase = record?.phase ?: app.latest.phase.takeIf { app.latest.deploymentId == deploymentId } ?: QUEUED
        val updatedAt = record?.updatedAt ?: app.latest.at
        return when (phase) {
            SUCCEEDED -> HostingAgentDeployStatusResponse(
                done = true, status = SUCCEEDED, elapsed = elapsed, url = app.url, build = record?.build,
                hint = record?.build?.correctionNotice ?: record?.let { retryNotice(app, it) },
                next = if (app.uploaded) "GitHub 레포를 연결하면 기본 브랜치에 push 할 때마다 자동으로 배포돼요: ${webAppProperties.url.trimEnd('/')}/?go=hosting" else null,
            )
            FAILED -> {
                val retry = record?.let { hostingStartRetries.follow(deploy.userId, app, it) }
                when (retry?.status) {
                    // 에이전트는 계속 기다린다. 보낸 재시도는 그 명령의 배포를 따라간다
                    HostingStartRetry.ANALYZING -> HostingAgentDeployStatusResponse(done = false, status = RETRYING, elapsed = elapsed)
                    HostingStartRetry.SENT -> {
                        hostingAgentDeploys.save(deploy.copy(commandId = retry?.commandId, deploymentId = null, generation = null, startedAt = Instant.now()))
                        HostingAgentDeployStatusResponse(done = false, status = RETRYING, elapsed = elapsed)
                    }
                    else -> failed(
                        elapsed,
                        record?.failure ?: HostingFailure("internal", "the deployment failed"),
                        log = record?.takeIf { it.hasLog }?.let { hostingApps.buildLogTail(app, deploymentId, LOG_TAIL_BYTES) },
                        flow = record?.let { flowNotice(app, it, retry) },
                    )
                }
            }
            else -> if (Duration.between(updatedAt, Instant.now()) > STALLED) {
                failed(elapsed, HostingFailure(STUCK, "the deployment has not moved"))
            } else {
                HostingAgentDeployStatusResponse(done = false, status = phase, elapsed = elapsed)
            }
        }
    }

    private fun failed(elapsed: Long, failure: HostingFailure, log: String? = null, flow: String? = null) = HostingAgentDeployStatusResponse(
        done = true, status = FAILED, elapsed = elapsed, failure = failure,
        hint = listOfNotNull(flow, HINTS[failure.code] ?: HINTS.getValue("internal")).joinToString(" "),
        logTail = log,
    )

    /** 재시도로 뜬 배포: 처음 명령과 AI 가 고른 명령, 다음에도 쓰려면 kukie.json 에 넣으라는 안내. */
    private fun retryNotice(app: HostingApp, record: HostingDeployment): String? {
        val retry = record.retry?.takeIf { record.build?.retried == true } ?: return null
        val first = hostingApps.deployment(app, retry.of)?.build?.start
        return HostingStartFlow.succeeded(first, retry.start)
    }

    /** 시작하지 못한 배포에서 서버가 해 본 것: 규칙 보정, AI 재시도와 그 결과. 없으면 null. */
    private fun flowNotice(app: HostingApp, record: HostingDeployment, retry: HostingStartRetry?): String? {
        if (record.failure?.code != START_FAILED) return null
        val first = record.retry?.of?.let { hostingApps.deployment(app, it) }
        return HostingStartFlow.failed(first ?: record, record.takeIf { first != null }, retry)
    }

    companion object {
        private const val QUEUED = "queued"
        private const val RETRYING = "retrying"
        private const val START_FAILED = "start_failed"
        private const val SUCCEEDED = "succeeded"
        private const val FAILED = "failed"
        private const val STUCK = "stuck"
        private const val APP_DELETED = "app_deleted"
        private const val LOG_TAIL_BYTES = 8192
        private const val WAITS_PER_MINUTE = 30
        private val CHECK_INTERVAL: Duration = Duration.ofSeconds(3)
        private val SEARCH_INTERVAL: Duration = Duration.ofSeconds(30)

        // 배포기가 멈추면 받은 명령은 30분 뒤 다시 나와 새 배포가 된다. 그보다 길게 두어 다시 한 배포를 따라갈 틈을 준다
        private val STALLED: Duration = Duration.ofMinutes(45)

        // 배포기는 2시간 넘은 업로드 명령을 거절한다. 그보다 길게 두어, 응답 없음 뒤 새로 올린 것을 옛 명령이 덮지 않게 한다
        private val QUEUED_LIMIT: Duration = Duration.ofMinutes(130)

        /** 에이전트가 사용자에게 그대로 전해도 되는 안내. */
        private val HINTS = mapOf(
            "config_invalid" to "kukie.json 이 형식에 맞지 않습니다. failure.message 대로 고친 뒤 새 배포를 만드세요.",
            "not_detected" to "어떤 앱인지 알아내지 못했습니다. Dockerfile 을 추가한 뒤 새 배포를 만드세요.",
            "no_dockerfile" to "어떤 앱인지 알아내지 못했습니다. Dockerfile 을 추가한 뒤 새 배포를 만드세요.",
            "no_start" to "시작 명령을 찾지 못했습니다. kukie.json 에 start 를 적거나 start 스크립트를 만든 뒤 새 배포를 만드세요.",
            "start_mismatch" to "시작 명령이 가리키는 Python 모듈이 프로젝트에 없습니다. failure.message 에 kukie.json 예시가 있으면 그대로 루트의 kukie.json 에 넣고, 없으면 앱 객체가 있는 파일로 start 를 적은 뒤 새 배포를 만드세요.",
            "wrong_root" to "올린 폴더 맨 위에 앱이 없습니다. failure.message 에 적힌 하위 폴더 안에서 다시 묶어 새 배포를 만드세요.",
            "build_failed" to "빌드가 실패했습니다. logTail 의 오류를 보고 코드를 고친 뒤 새 배포를 만드세요.",
            "build_timeout" to "빌드가 15분을 넘었습니다. 빌드를 가볍게 하거나 Dockerfile 을 만든 뒤 새 배포를 만드세요.",
            "start_failed" to "앱이 뜨지 않았습니다. logTail 의 === app === 아래 출력을 보고, 앱이 PORT 환경변수의 포트에서 받는지와 빠진 의존성이 없는지 확인하세요. 시작 명령이 틀렸으면 맞는 명령을 루트의 kukie.json 에 {\"start\": \"...\"} 로 적은 뒤 새 배포를 만드세요.",
            "app_limit" to "앱이 이미 3개입니다. 앱을 지우거나 있는 앱 이름으로 배포하세요.",
            "not_allowed" to "아직 호스팅을 쓸 수 없는 계정입니다. 사용자에게 Kukie 의 앱 배포 화면에서 사용 신청을 먼저 하라고 전하세요.",
            "invalid_team_name" to "이 GitHub 계정 이름으로는 앱을 만들 수 없습니다.",
            "invalid_app_name" to "앱 이름이 규칙에 맞지 않습니다. 이름을 바꿔 새 배포를 만드세요.",
            "name_too_long" to "계정 이름과 앱 이름을 합친 길이가 깁니다. 더 짧은 앱 이름으로 새 배포를 만드세요.",
            "source_mismatch" to "이 이름의 앱은 레포에 연결돼 있습니다. 그 레포에 push 하면 배포됩니다.",
            "app_name_taken" to "이 이름의 앱은 다른 GitHub 계정이 올린 앱입니다. 다른 앱 이름으로 새 배포를 만드세요.",
            "account_renamed" to "GitHub 계정 이름을 바꾸기 전 이름의 팀이 남아 있어 배포할 수 없습니다. 다시 시도하지 말고, 사용자에게 Kukie 에 문의해 예전 팀을 지운 뒤 배포하라고 전하세요.",
            APP_DELETED to "배포하는 동안 앱이 지워졌습니다. 다시 배포하려면 새 배포를 만드세요.",
            "internal" to "Kukie 쪽 문제입니다. 한 번만 새 배포를 만들어 다시 해 보고, 또 실패하면 멈추세요.",
            "deploy_failed" to "Kukie 쪽 문제입니다. 한 번만 새 배포를 만들어 다시 해 보고, 또 실패하면 멈추세요.",
            STUCK to "배포가 오래 진행되지 않습니다. 한 번만 새 배포를 만들어 다시 해 보고, 또 그러면 멈추세요.",
        )
    }
}
