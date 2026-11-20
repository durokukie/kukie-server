package com.duro.kukie.hosting.presentation.dto.response

import com.duro.kukie.hosting.domain.HostingApp
import com.duro.kukie.hosting.domain.HostingConfig
import com.duro.kukie.hosting.domain.HostingDbExport
import com.duro.kukie.hosting.domain.HostingExit
import com.duro.kukie.hosting.domain.HostingFailure
import com.duro.kukie.hosting.domain.HostingDeployment
import com.duro.kukie.hosting.domain.HostingIdentity
import com.duro.kukie.hosting.domain.HostingLatest
import com.duro.kukie.hosting.domain.HostingPastApp
import com.duro.kukie.hosting.domain.HostingRepoItem
import com.duro.kukie.hosting.domain.HostingRepoList
import com.duro.kukie.hosting.domain.HostingRuntime
import com.duro.kukie.hosting.domain.HostingSource
import com.duro.kukie.hosting.domain.HostingStartRetry
import com.duro.kukie.hosting.domain.HostingSuspension
import com.duro.kukie.hosting.domain.HostingTerms
import com.duro.kukie.hosting.domain.SkippedPush
import java.time.Instant
import java.time.LocalDate

data class HostingAppsResponse(
    /** 연결한 GitHub 계정명. null 이면 Kukie 계정으로 쓰고 있어 업로드로만 배포한다(레포 배포는 GitHub 를 연결한 뒤). */
    val githubLogin: String?,
    /** 앱 주소에 들어가는 팀 이름(<앱>--<팀>). */
    val team: String,
    /** 호스팅을 쓸 수 있는 계정인지. false 면 사용 신청(POST /hosting/membership)을 먼저 해야 한다. */
    val joined: Boolean,
    /** 서비스 종료일. 이날 앱과 데이터가 지워진다. 신청 전에 보여 준다. */
    val closesOn: LocalDate,
    val appLimit: Int,
    val apps: List<HostingAppResponse>,
    /** 배포를 시작하지 못한 마지막 요청이나 push. 없으면 null. */
    val skippedPush: SkippedPushResponse?,
    /** GitHub App 설치. null 이면 App 을 아직 설치하지 않았다. */
    val installation: HostingInstallationResponse?,
    /** GitHub App 설치 페이지. 허용 레포 바꾸기도 같은 주소다. */
    val installUrl: String,
) {
    companion object {
        private const val APP_LIMIT = 3

        private fun installUrl(githubApp: String) = "https://github.com/apps/$githubApp/installations/new"

        fun of(
            githubApp: String,
            account: HostingIdentity,
            joined: Boolean,
            apps: List<HostingApp>,
            suspensions: Map<String, HostingSuspension?>,
            skipped: SkippedPush?,
            repos: HostingRepoList?,
        ) = HostingAppsResponse(
            githubLogin = account.login.takeIf { account.gitHub },
            team = account.team,
            joined = joined,
            closesOn = HostingTerms.CLOSES_ON,
            appLimit = APP_LIMIT,
            apps = apps.map { HostingAppResponse.from(it, suspensions[it.app]) },
            skippedPush = skipped?.let(SkippedPushResponse::from),
            installation = repos?.let { HostingInstallationResponse(it.allowed, it.repos) },
            installUrl = installUrl(githubApp),
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
    /** 앱을 배포하는 레포. 업로드로 만들고 레포를 연결하지 않은 앱은 null. */
    val repository: String?,
    /** 코딩 에이전트가 올린 폴더로 만든 앱. 레포를 연결하면 push 마다 배포되고 false 가 된다. */
    val uploaded: Boolean,
    val url: String,
    /** 마지막 배포에 쓴 값. latest.phase 가 failed 면 실행 중인 앱의 값이 아닐 수 있다. */
    val config: HostingConfig,
    /** 마지막 배포의 요약. 배포 기록이 지워진 뒤에도 남는다. */
    val latest: HostingLatest,
    /** 정지된 앱이면 그 사유와 시각. 정지된 앱은 방문해도 깨어나지 않고, 배포·레포 연결·삭제를 할 수 없다. */
    val suspended: HostingSuspensionResponse?,
) {
    companion object {
        fun from(app: HostingApp, suspension: HostingSuspension?) = HostingAppResponse(
            app.app, app.repo?.fullName, app.uploaded, app.url, app.config, app.latest,
            suspension?.let { HostingSuspensionResponse(it.code, it.at) },
        )
    }
}

data class HostingSuspensionResponse(
    /** abuse_mining, abuse_mail, abuse_scan, abuse_probe, admin. 모르는 값은 그냥 "정지됨"으로 보여 준다. */
    val code: String,
    val at: Instant,
)

data class SkippedPushResponse(
    /** 업로드 명령이었으면 null. */
    val repository: String?,
    /** 배포기가 앱 이름을 알았을 때만. */
    val app: String?,
    /** 거절한 명령의 id. push 였으면 null. 명령의 결과는 이것으로 찾는다. 사용 신청이 거절됐으면 repository 와 app 이 없다. */
    val commandId: String?,
    /**
     * not_allowed, invalid_team_name, invalid_app_name, name_too_long, queue_full, auto_deploy_off, app_limit,
     * app_name_taken, account_renamed, source_mismatch, repository_in_use, suspended, internal
     */
    val reason: String,
    val at: Instant,
) {
    companion object {
        fun from(skipped: SkippedPush) = SkippedPushResponse(skipped.repo?.fullName, skipped.app, skipped.commandId, skipped.reason, skipped.at)
    }
}

data class HostingAppDetailResponse(
    val app: HostingAppResponse,
    /** 앱이 지금 실행 중인지, 잠들었는지, 거듭 죽는지. 배포기가 아직 보지 않았으면 null. */
    val runtime: HostingRuntimeResponse?,
    /** 최신순, 20건까지. 30일이 지난 배포는 없다. */
    val deployments: List<HostingDeployment>,
    /** 이 서버에서 실패 원인 AI 분석을 쓸 수 있는지. */
    val analysisEnabled: Boolean,
    /**
     * 시작하지 못한(start_failed) 배포의 id 마다 서버가 한 일: AI 가 시작 명령을 고르는 중(analyzing), 그 명령으로 다시 배포함(sent),
     * 다시 배포하지 않음(skipped). 다시 배포한 배포는 deployments 에서 retry.of 가 그 id 다.
     */
    val startRetries: Map<String, HostingStartRetry> = emptyMap(),
) {
    companion object {
        fun of(
            app: HostingApp,
            suspension: HostingSuspension?,
            deployments: List<HostingDeployment>,
            analysisEnabled: Boolean,
            startRetries: Map<String, HostingStartRetry> = emptyMap(),
            runtime: HostingRuntime? = null,
        ) = HostingAppDetailResponse(
            HostingAppResponse.from(app, suspension), runtime?.let(HostingRuntimeResponse::from), deployments, analysisEnabled, startRetries,
        )
    }
}

data class HostingCommandResponse(
    /** 배포 시작과 재배포는 앱의 latest.commandId 가 이 값이 되면 시작된 것이다. */
    val commandId: String,
)

/** AI 가 로그를 보고 추정한 원인. 틀릴 수 있으므로 화면에 AI 추정이라고 밝힌다. */
data class HostingFailureAnalysisResponse(
    val cause: String,
    /** 고칠 방법. 비어 있을 수 있다. */
    val fixes: List<String>,
    /** 같은 배포를 전에 분석한 결과를 다시 준 것이다. 하루 횟수를 쓰지 않는다. */
    val cached: Boolean = false,
)

data class HostingRuntimeResponse(
    /** running(실행 중), starting(띄우는 중), crashing(앱이 거듭 죽음), sleeping(잠듦, 방문하면 깨어남). 모르는 값도 올 수 있다. */
    val state: String,
    val replicas: Int,
    val ready: Int,
    /** 지금 Pod 의 앱 컨테이너가 다시 시작한 횟수. 새로 배포하면 0 부터 센다. */
    val restarts: Int,
    /** 앱 컨테이너가 마지막으로 끝난 까닭. OOMKilled 면 메모리 부족이다. */
    val lastExit: HostingExit?,
    /** 이 상태가 된 시각. */
    val since: Instant,
) {
    companion object {
        fun from(runtime: HostingRuntime) =
            HostingRuntimeResponse(runtime.state, runtime.replicas, runtime.ready, runtime.restarts, runtime.lastExit, runtime.since)
    }
}

/** 앱 기록 내려받기. 서비스가 끝나면 돌려주지 않으므로 사용자가 받아 두는 파일이다. */
data class HostingAppRecordResponse(
    val exportedAt: Instant,
    val app: HostingAppResponse,
    val runtime: HostingRuntimeResponse?,
    /** 남아 있는 배포 기록 전부(최신순). 30일이 지난 배포는 없다. */
    val deployments: List<HostingDeployment>,
) {
    companion object {
        fun of(at: Instant, app: HostingApp, suspension: HostingSuspension?, runtime: HostingRuntime?, deployments: List<HostingDeployment>) =
            HostingAppRecordResponse(at, HostingAppResponse.from(app, suspension), runtime?.let(HostingRuntimeResponse::from), deployments)
    }
}

data class HostingDbExportsResponse(
    /** 최신순, 10건까지. 30일이 지난 내보내기는 없다. */
    val exports: List<HostingDbExportResponse>,
)

data class HostingDbExportResponse(
    val exportId: String,
    /** running(내보내는 중), succeeded, failed. 요청한 뒤 이 목록에 나올 때까지 몇 분 걸릴 수 있다. */
    val phase: String,
    /** gzip 크기. */
    val bytes: Long?,
    /** failed 일 때: no_db, db_not_ready, dump_failed, busy, stale, internal. */
    val failure: HostingFailure?,
    val startedAt: Instant,
    val updatedAt: Instant,
    /** succeeded 일 때 .sql.gz 를 받는 주소. 10분 동안 쓸 수 있고, 목록을 다시 읽으면 새로 준다. */
    val downloadUrl: String?,
) {
    companion object {
        fun of(export: HostingDbExport, downloadUrl: String?) = HostingDbExportResponse(
            export.exportId, export.phase, export.bytes, export.failure, export.startedAt, export.updatedAt, downloadUrl,
        )
    }
}

data class HostingHistoryResponse(
    /** 지운 앱과, 지우고 같은 이름으로 다시 만든 앱의 그 전 세대. 최근 배포순, 20개까지. 배포 기록이 30일 뒤 지워지면 빠진다. */
    val apps: List<HostingPastAppResponse>,
) {
    companion object {
        fun of(apps: List<HostingPastApp>) = HostingHistoryResponse(apps.map(HostingPastAppResponse::from))
    }
}

data class HostingPastAppResponse(
    val app: String,
    /** 빌드 로그 주소에 넣는 앱 세대(앱을 처음 만든 시각). */
    val generation: String,
    /** GitHub 레포 이름(owner/repo). 업로드 앱이거나 2026-11-20 전 기록이면 null. */
    val repository: String?,
    val uploaded: Boolean,
    /** 최신순, 20건까지. */
    val deployments: List<HostingDeployment>,
) {
    companion object {
        fun from(app: HostingPastApp) = HostingPastAppResponse(
            app.app,
            app.generation,
            app.latest.repo?.fullName,
            app.latest.source?.type == HostingSource.UPLOAD,
            app.deployments,
        )
    }
}
