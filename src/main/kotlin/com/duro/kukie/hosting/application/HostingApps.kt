package com.duro.kukie.hosting.application

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingStateStore
import com.duro.kukie.hosting.domain.HostingAllowedList
import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingFile
import com.duro.kukie.hosting.domain.HostingIdentity
import com.duro.kukie.hosting.domain.HostingMember
import com.duro.kukie.hosting.domain.HostingRepoList
import com.duro.kukie.hosting.domain.HostingSuspension
import com.duro.kukie.hosting.domain.SkippedPush
import com.duro.kukie.hosting.exception.HostingAppNotFoundException
import com.duro.kukie.hosting.exception.HostingAppSuspendedException
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.user.domain.GitHubAccount
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** 배포기가 적은 상태 파일에서 회원의 앱을 읽는다. 앱의 주인은 계정명이 아니라 숫자 id 로 가린다. */
@Component
class HostingApps(
    private val hostingProperties: HostingProperties,
    private val hostingStateStore: HostingStateStore,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {

    private val log = logger()

    /** GitHub 를 연결했으면 그 계정, 아니면 회원의 Kukie 계정. 연결을 바꾸면 다른 팀이 되어 그 전 팀의 앱은 보이지 않는다. */
    fun accountOf(userId: UUID): HostingIdentity {
        if (!hostingProperties.enabled) throw HostingDisabledException()
        val user = userRepository.findByIdOrThrow(userId)
        val gitHub = user.githubId?.let { id -> user.githubLogin?.let { GitHubAccount(id, it) } }
        return HostingIdentity.of(userId, gitHub)
    }

    fun ownedBy(account: HostingIdentity): List<HostingApp> =
        hostingStateStore.list("apps/${account.team}/")
            .filter { it.endsWith(".json") }
            .mapNotNull { read<HostingApp>(it) }
            .filter { it.owner.id == account.id }

    /** 팀 폴더에 있는 앱 파일, 주인이 누구든. 계정명을 다른 사람이 다시 쓴 경우를 가리는 데 쓴다. */
    fun appInTeam(account: HostingIdentity, app: String): HostingApp? =
        app.takeIf { APP_NAME.matches(it) }?.let { read<HostingApp>("apps/${account.team}/$it.json") }

    /** 계정의 앱. 없거나 주인이 다르면 null. */
    fun appOf(account: HostingIdentity, app: String): HostingApp? =
        app.takeIf { APP_NAME.matches(it) }?.let { read<HostingApp>("apps/${account.team}/$it.json") }?.takeIf { it.owner.id == account.id }

    /** 지금 세대의 배포 기록 하나. 30일이 지났으면 null. */
    fun deployment(app: HostingApp, deploymentId: String): HostingDeployment? =
        deploymentId.takeIf { DEPLOYMENT_ID.matches(it) }?.let { read<HostingDeployment>("${app.deploymentPrefix}$it.json") }

    /** 거절한 마지막 push 나 명령(가리지 않은 그대로). */
    fun lastSkipped(account: HostingIdentity): SkippedPush? =
        read<SkippedPush>("apps/_skipped/${account.team}.json")?.takeIf { it.sender.id == account.id }

    fun requireOwned(userId: UUID, app: String): HostingApp {
        val account = accountOf(userId)
        if (!APP_NAME.matches(app)) throw HostingAppNotFoundException()
        return read<HostingApp>("apps/${account.team}/$app.json")?.takeIf { it.owner.id == account.id }
            ?: throw HostingAppNotFoundException()
    }

    /** 앱이 정지돼 있으면 그 기록. 주인 확인을 거친 앱에만 쓴다. */
    fun suspension(app: HostingApp): HostingSuspension? =
        read<HostingSuspension>("apps/_suspended/${app.team}/${app.app}.json")?.takeIf { it.team == app.team && it.app == app.app }

    /** 정지된 앱은 배포기가 배포도 삭제도 거부하므로, 명령을 보내기 전에 여기서 막는다. */
    fun requireNotSuspended(app: HostingApp) {
        if (suspension(app) != null) throw HostingAppSuspendedException()
    }

    /**
     * 배포기가 받아 주는 계정인지: 가입했거나(apps/_members/<id>.json) 운영자가 넣어 준 계정(apps/_allowed.json).
     * 둘 다 없으면 App 을 설치한 계정의 레포 목록에 적힌 값.
     */
    fun allowed(account: HostingIdentity): Boolean {
        if (read<HostingMember>("apps/_members/${account.id}.json")?.owner?.id == account.id) return true
        val list = read<HostingAllowedList>("apps/_allowed.json") ?: return repoList(account)?.allowed == true
        // ids 가 있으면 id 로만 본다(login 은 다른 계정이 가져갈 수 있다). 옛 배포기의 파일(logins 만)이면 계정명으로 본다
        list.ids?.let { return account.id in it }
        return list.logins.orEmpty().any { it.equals(account.team, ignoreCase = true) }
    }

    /** 계정이 GitHub App 에 허용한 레포. App 을 설치하지 않았으면 null. */
    fun repoList(account: HostingIdentity): HostingRepoList? =
        read<HostingRepoList>("apps/_repos/${account.team}.json")?.takeIf { it.owner.id == account.id }

    /**
     * 배포를 시작하지 못한 마지막 push 나 명령. 그 뒤 같은 레포(업로드 명령이면 같은 이름)의 앱이 배포됐으면 지난 일이라 주지 않는다.
     * 앱의 출처가 맞지 않거나 레포를 이미 다른 앱이 써서 거절한 것은 다른 앱이 배포돼도 풀리지 않아 가리지 않는다.
     */
    fun skippedPush(account: HostingIdentity, apps: List<HostingApp>): SkippedPush? =
        read<SkippedPush>("apps/_skipped/${account.team}.json")
            ?.takeIf { it.sender.id == account.id }
            ?.takeUnless { skipped -> skipped.reason !in UNRESOLVED_BY_DEPLOYS && apps.any { it.isFor(skipped) && it.latest.at > skipped.at } }

    private fun HostingApp.isFor(skipped: SkippedPush): Boolean {
        val skippedRepo = skipped.repo?.fullName ?: return skipped.app == app
        return repo?.fullName.equals(skippedRepo, ignoreCase = true)
    }

    /** 지금 세대의 배포를 최신순으로 준다. 기록은 30일 뒤 지워지므로 비어 있을 수 있다. */
    fun deployments(app: HostingApp): List<HostingDeployment> =
        hostingStateStore.list(app.deploymentPrefix)
            .filter { it.endsWith(".json") }
            // 배포 id가 시각(yyyyMMdd'T'HHmmss'Z')으로 시작해서 키 순서가 곧 시간 순서다
            .takeLast(MAX_DEPLOYMENTS)
            .reversed()
            .mapNotNull { read<HostingDeployment>(it) }

    fun buildLogTail(app: HostingApp, deploymentId: String, bytes: Int): String? {
        if (!DEPLOYMENT_ID.matches(deploymentId)) return null
        return hostingStateStore.readTail("${app.deploymentPrefix}$deploymentId.log", bytes)
    }

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

    private val HostingApp.deploymentPrefix: String
        get() = "deployments/$team/$app/$generation/"

    companion object {
        private const val MAX_DEPLOYMENTS = 20
        private val UNRESOLVED_BY_DEPLOYS = setOf("source_mismatch", "repository_in_use")

        // 경로에서 받은 값을 버킷 키에 넣기 전에 배포기가 만드는 이름 모양인지 본다
        private val APP_NAME = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
        private val DEPLOYMENT_ID = Regex("\\d{8}T\\d{6}Z-[0-9a-f]{7}")
    }
}
