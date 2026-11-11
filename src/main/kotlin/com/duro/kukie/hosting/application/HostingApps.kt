package com.duro.kukie.hosting.application

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingStateStore
import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingFile
import com.duro.kukie.hosting.domain.SkippedPush
import com.duro.kukie.hosting.exception.HostingAppNotFoundException
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** 배포기가 적은 상태 파일에서 회원의 앱을 읽는다. 앱의 주인은 계정명이 아니라 GitHub 숫자 id 로 가린다. */
@Component
class HostingApps(
    private val hostingProperties: HostingProperties,
    private val hostingStateStore: HostingStateStore,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {

    private val log = logger()

    /** 연결하지 않았으면 null. */
    fun accountOf(userId: UUID): GitHubAccount? {
        if (!hostingProperties.enabled) throw HostingDisabledException()
        val user = userRepository.findByIdOrThrow(userId)
        return GitHubAccount(user.githubId ?: return null, user.githubLogin ?: return null)
    }

    fun ownedBy(account: GitHubAccount): List<HostingApp> =
        hostingStateStore.list("apps/${account.team}/")
            .filter { it.endsWith(".json") }
            .mapNotNull { read<HostingApp>(it) }
            .filter { it.owner.id == account.id }

    fun requireOwned(userId: UUID, app: String): HostingApp {
        val account = accountOf(userId) ?: throw HostingAppNotFoundException()
        if (!APP_NAME.matches(app)) throw HostingAppNotFoundException()
        return read<HostingApp>("apps/${account.team}/$app.json")?.takeIf { it.owner.id == account.id }
            ?: throw HostingAppNotFoundException()
    }

    /** 배포를 시작하지 못한 마지막 push. 그 뒤 같은 레포의 앱이 배포됐으면 지난 일이라 주지 않는다. */
    fun skippedPush(account: GitHubAccount, apps: List<HostingApp>): SkippedPush? =
        read<SkippedPush>("apps/_skipped/${account.team}.json")
            ?.takeIf { it.sender.id == account.id }
            ?.takeUnless { skipped ->
                apps.any { it.repo.fullName.equals(skipped.repo.fullName, ignoreCase = true) && it.latest.at > skipped.at }
            }

    /** 지금 세대의 배포를 최신순으로 준다. 기록은 30일 뒤 지워지므로 비어 있을 수 있다. */
    fun deployments(app: HostingApp): List<HostingDeployment> =
        hostingStateStore.list(app.deploymentPrefix)
            .filter { it.endsWith(".json") }
            // 배포 id가 시각(yyyyMMdd'T'HHmmss'Z')으로 시작해서 키 순서가 곧 시간 순서다
            .takeLast(MAX_DEPLOYMENTS)
            .reversed()
            .mapNotNull { read<HostingDeployment>(it) }

    fun buildLog(app: HostingApp, deploymentId: String): String? {
        if (!DEPLOYMENT_ID.matches(deploymentId)) return null
        return hostingStateStore.read("${app.deploymentPrefix}$deploymentId.log")
    }

    private inline fun <reified T : HostingFile> read(key: String): T? =
        hostingStateStore.read(key)
            ?.let { body ->
                runCatching { objectMapper.readValue(body, T::class.java) }
                    .onFailure { log.warn("호스팅 상태 파일을 읽지 못했습니다: key={}, cause={}", key, it.message) }
                    .getOrNull()
            }
            ?.takeIf { file ->
                (file.version == HostingFile.VERSION).also { known ->
                    if (!known) log.warn("모르는 버전의 호스팅 상태 파일입니다: key={}, version={}", key, file.version)
                }
            }

    private val GitHubAccount.team: String
        get() = login.lowercase()

    private val HostingApp.deploymentPrefix: String
        get() = "deployments/$team/$app/$generation/"

    companion object {
        private const val MAX_DEPLOYMENTS = 20

        // 경로에서 받은 값을 버킷 키에 넣기 전에 배포기가 만드는 이름 모양인지 본다
        private val APP_NAME = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
        private val DEPLOYMENT_ID = Regex("\\d{8}T\\d{6}Z-[0-9a-f]{7}")
    }
}
