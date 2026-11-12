package com.duro.kukie.hosting.presentation.dto.response

import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingConfig
import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingLatest
import com.duro.kukie.hosting.domain.HostingRepoItem
import com.duro.kukie.hosting.domain.HostingRepoList
import com.duro.kukie.hosting.domain.SkippedPush
import com.duro.kukie.user.domain.GitHubAccount
import java.time.Instant

data class HostingAppsResponse(
    /** 연결한 GitHub 계정명. null 이면 먼저 계정을 연결해야 한다. */
    val githubLogin: String?,
    val appLimit: Int,
    val apps: List<HostingAppResponse>,
    /** 배포를 시작하지 못한 마지막 요청이나 push. 없으면 null. */
    val skippedPush: SkippedPushResponse?,
    /** GitHub App 설치. null 이면 App 을 아직 설치하지 않았다. */
    val installation: HostingInstallationResponse?,
) {
    companion object {
        private const val APP_LIMIT = 3

        fun notLinked() = HostingAppsResponse(null, APP_LIMIT, emptyList(), null, null)

        fun of(account: GitHubAccount, apps: List<HostingApp>, skipped: SkippedPush?, repos: HostingRepoList?) = HostingAppsResponse(
            githubLogin = account.login,
            appLimit = APP_LIMIT,
            apps = apps.map(HostingAppResponse::from),
            skippedPush = skipped?.let(SkippedPushResponse::from),
            installation = repos?.let { HostingInstallationResponse(it.allowed, it.repos) },
        )
    }
}

data class HostingInstallationResponse(
    /** false 면 아직 호스팅을 쓸 수 없는 계정이다. 배포를 시작할 수 없다. */
    val allowed: Boolean,
    /** App 에 읽기를 허용한 레포. 이 가운데서 골라 배포를 시작한다. */
    val repositories: List<HostingRepoItem>,
)

data class HostingAppResponse(
    val app: String,
    val repository: String,
    val url: String,
    /** 마지막 배포에 쓴 값. latest.phase 가 failed 면 실행 중인 앱의 값이 아닐 수 있다. */
    val config: HostingConfig,
    /** 마지막 배포의 요약. 배포 기록이 지워진 뒤에도 남는다. */
    val latest: HostingLatest,
) {
    companion object {
        fun from(app: HostingApp) = HostingAppResponse(app.app, app.repo.fullName, app.url, app.config, app.latest)
    }
}

data class SkippedPushResponse(
    val repository: String,
    /** not_allowed, invalid_team_name, invalid_app_name, name_too_long, queue_full, app_limit, internal */
    val reason: String,
    val at: Instant,
) {
    companion object {
        fun from(skipped: SkippedPush) = SkippedPushResponse(skipped.repo.fullName, skipped.reason, skipped.at)
    }
}

data class HostingAppDetailResponse(
    val app: HostingAppResponse,
    /** 최신순, 20건까지. 30일이 지난 배포는 없다. */
    val deployments: List<HostingDeployment>,
) {
    companion object {
        fun of(app: HostingApp, deployments: List<HostingDeployment>) =
            HostingAppDetailResponse(HostingAppResponse.from(app), deployments)
    }
}

data class HostingCommandResponse(
    /** 배포 시작과 재배포는 앱의 latest.commandId 가 이 값이 되면 시작된 것이다. */
    val commandId: String,
)
