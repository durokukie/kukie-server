package com.duro.kukie.hosting.application

import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.`in`.CreateHostingAgentDeployCommand
import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.hosting.application.port.out.HostingUploadStore
import com.duro.kukie.hosting.domain.HostingAgentDeploy
import com.duro.kukie.hosting.exception.HostingAppLimitException
import com.duro.kukie.hosting.exception.HostingAppSourceMismatchException
import com.duro.kukie.hosting.exception.HostingDeployInProgressException
import com.duro.kukie.hosting.exception.HostingGitHubNotLinkedException
import com.duro.kukie.hosting.exception.HostingInvalidAppNameException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingTooManyDeploysException
import com.duro.kukie.hosting.exception.HostingUploadTooLargeException
import com.duro.kukie.hosting.infrastructure.S3HostingUploadStore
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentDeployResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentUploadResponse
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class CreateHostingAgentDeployService(
    private val hostingApps: HostingApps,
    private val hostingAgentDeploys: HostingAgentDeploys,
    private val hostingAgentRateLimit: HostingAgentRateLimit,
    private val hostingUploadStore: HostingUploadStore,
) {

    private val log = logger()
    private val random = SecureRandom()

    /** 에이전트가 폴더를 올릴 주소를 준다. 업로드 키는 서버가 정한다. 같은 이름의 업로드 앱이 있으면 그 앱의 새 버전이 된다. */
    operator fun invoke(command: CreateHostingAgentDeployCommand): HostingAgentDeployResponse {
        val account = hostingApps.accountOf(command.userId) ?: throw HostingGitHubNotLinkedException()
        if (!hostingApps.allowed(account)) throw HostingNotAllowedException()
        val team = account.login.lowercase()
        if (!HostingAgentDeploys.validName(team, command.name)) {
            val suggestion = HostingAgentDeploys.suggest(command.name, team)
                ?: throw HostingInvalidAppNameException("GitHub 계정 이름이 길어 앱을 만들 수 없습니다.")
            throw HostingInvalidAppNameException("앱 이름이 규칙에 맞지 않거나 계정 이름과 합쳐 너무 깁니다. 예: $suggestion")
        }
        if (command.bytes > MAX_UPLOAD_BYTES) throw HostingUploadTooLargeException()
        // 계정명을 다른 사람이 다시 쓰면 그 폴더에 옛 주인의 앱이 남아 있다. 그 주소를 덮지 않는다
        if (hostingApps.appInTeam(account, command.name)?.let { it.owner.id != account.id } == true) {
            throw HostingInvalidAppNameException("이 이름은 쓸 수 없습니다. 다른 이름으로 배포해 주세요.")
        }
        val apps = hostingApps.ownedBy(account)
        val existing = apps.firstOrNull { it.app == command.name }
        if (existing != null && existing.repo != null) {
            throw HostingAppSourceMismatchException("이 이름의 앱은 레포 ${existing.repo.fullName} 에 연결돼 있습니다. 그 레포에 push 하면 배포됩니다.")
        }
        if (existing == null && apps.size >= APP_LIMIT) throw HostingAppLimitException()
        hostingAgentDeploys.activeOf(command.userId, team)?.let { throw HostingDeployInProgressException("진행 중인 배포가 있습니다: $it. 그 배포가 끝난 뒤 다시 시도해 주세요.") }
        if (!hostingAgentRateLimit.tryAcquire("deploys:$team", DEPLOYS_PER_HOUR, Duration.ofHours(1))) throw HostingTooManyDeploysException()

        val deploy = HostingAgentDeploy(
            id = ByteArray(20).also(random::nextBytes).joinToString("") { "%02x".format(it) },
            userId = command.userId,
            githubId = account.id,
            login = account.login,
            team = team,
            app = command.name,
            bytes = command.bytes,
            createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS),
        )
        hostingAgentDeploys.save(deploy)
        val url = hostingUploadStore.presignPut(deploy.key, deploy.bytes, UPLOAD_URL_TTL)
        log.info("에이전트 배포를 만들었습니다: user={}, app={}/{}, deploy={}", command.userId, team, deploy.app, deploy.id)

        return HostingAgentDeployResponse(
            deployId = deploy.id,
            app = deploy.app,
            existing = existing != null,
            lastDeployedAt = existing?.latest?.at,
            upload = HostingAgentUploadResponse(url, S3HostingUploadStore.CONTENT_TYPE, UPLOAD_URL_TTL.seconds),
        )
    }

    companion object {
        const val MAX_UPLOAD_BYTES = 50L * 1024 * 1024
        private const val APP_LIMIT = 3
        private const val DEPLOYS_PER_HOUR = 10
        private val UPLOAD_URL_TTL: Duration = Duration.ofMinutes(10)
    }
}
