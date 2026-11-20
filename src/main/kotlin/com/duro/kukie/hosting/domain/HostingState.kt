package com.duro.kukie.hosting.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

/** 배포기가 적는 파일의 모양. 모르는 필드는 무시하고, 모르는 version 의 파일은 읽지 않는다. */
interface HostingFile {
    val version: Int

    companion object {
        const val VERSION = 1
    }
}

data class HostingAccount(val id: Long, val login: String? = null, val type: String? = null)

data class HostingRepo(val id: Long? = null, val fullName: String)

/** 레포 없이 만든 앱의 출처. 코딩 에이전트가 올린 폴더로 만든 앱은 type 이 upload 다. */
data class HostingSource(val type: String) {
    companion object {
        const val UPLOAD = "upload"
    }
}

data class HostingCommit(val sha: String, val message: String? = null)

/**
 * start 는 레포의 kukie.json 이 정한 시작 명령. Dockerfile 이 없는 레포에서만 쓴다.
 * manual 이 true 면 kukie.json 이 자동 배포를 껐다(autoDeploy false). push 는 배포되지 않고 요청한 배포만 된다.
 */
data class HostingConfig(
    val port: Int,
    val size: String,
    val source: String,
    val start: String? = null,
    val manual: Boolean? = null,
)

/**
 * 커밋이 어떻게 빌드됐는지. method 는 dockerfile(레포의 Dockerfile) 또는 auto(Dockerfile 이 없어 배포기가 알아냄).
 * 나머지는 auto 일 때 알아낸 언어·버전과 앱을 시작하는 명령이다. 레포가 정할 수 있는 값이라 보여 주기만 한다.
 * corrected 는 알아낸 시작 명령이 없는 모듈을 가리켜 배포기가 start 로 고쳐 빌드했을 때 처음 알아낸 명령이다.
 */
data class HostingBuild(
    val method: String,
    val providers: List<String>? = null,
    val versions: Map<String, String>? = null,
    val start: String? = null,
    val corrected: String? = null,
) {
    /** 배포기가 시작 명령을 고쳤을 때 에이전트가 사용자에게 전할 안내. */
    val correctionNotice: String?
        @JsonIgnore get() {
            if (corrected == null || start == null) return null
            val json = start.replace("\\", "\\\\").replace("\"", "\\\"")
            return "시작 명령 '$corrected' 가 가리키는 모듈이 없어 '$start' 로 바꿔 배포했어요. " +
                "다음에도 이 명령으로 배포하려면 루트의 kukie.json 에 {\"start\": \"$json\"} 를 넣으세요."
        }
}

data class HostingFailure(val code: String, val message: String)

data class HostingLatest(
    val deploymentId: String,
    val commandId: String? = null,
    val phase: String,
    val commit: HostingCommit,
    val at: Instant,
)

/** repo 와 source 가운데 하나만 있다. 업로드로 만든 앱은 repo 가 없다. */
data class HostingApp(
    override val version: Int,
    val owner: HostingAccount,
    val repo: HostingRepo? = null,
    val source: HostingSource? = null,
    val team: String,
    val app: String,
    val generation: String,
    val url: String,
    val config: HostingConfig,
    val latest: HostingLatest,
) : HostingFile {
    val uploaded: Boolean
        @JsonIgnore get() = source?.type == HostingSource.UPLOAD
}

data class HostingDeployment(
    override val version: Int,
    val deploymentId: String,
    val phase: String,
    val trigger: String,
    val commandId: String? = null,
    val commit: HostingCommit,
    val config: HostingConfig,
    /** 지금 빌드한 배포에만 있다. 이미 빌드한 커밋을 다시 쓴 배포(imageReused)에는 없다. */
    val build: HostingBuild? = null,
    val imageReused: Boolean,
    val hasLog: Boolean,
    val failure: HostingFailure? = null,
    val startedAt: Instant,
    val updatedAt: Instant,
) : HostingFile

/** 악용이나 관리자 판단으로 정지된 앱(apps/_suspended/<team>/<app>.json). 정지가 풀리거나 앱을 지우면 배포기가 지운다. */
data class HostingSuspension(
    override val version: Int,
    val team: String,
    val app: String,
    /** abuse_mining, abuse_mail, abuse_scan, abuse_probe, admin. 모르는 값도 올 수 있다. */
    val code: String,
    val at: Instant,
) : HostingFile

/** 계정이 GitHub App 에 읽기를 허용한 레포. 파일이 없으면 App 을 설치하지 않은 것이다. */
data class HostingRepoList(
    override val version: Int,
    val owner: HostingAccount,
    /** false 면 아직 호스팅을 쓸 수 없는 계정이라 배포기가 배포 명령을 거절한다. */
    val allowed: Boolean,
    val repos: List<HostingRepoItem>,
    val at: Instant,
) : HostingFile

/** 가입한 계정 하나(apps/_members/<GitHub id>.json). 배포기가 가입 명령을 처리하고 쓴다. */
data class HostingMember(
    override val version: Int,
    val owner: HostingAccount,
) : HostingFile

/**
 * 운영자가 가입 절차 없이 넣어 준 계정. 배포기가 시작할 때마다 다시 쓴다. 지금 배포기(kukie-hosting-platform#17 뒤)는 GitHub 숫자 id(ids)를 쓴다:
 * login 은 바뀌고 다른 계정이 가져갈 수 있다. 그 전 배포기는 소문자 계정명(logins)을 썼다.
 */
data class HostingAllowedList(
    override val version: Int,
    val ids: List<Long>? = null,
    // TODO(kukie-hosting-platform#17 이미지가 dev·prod 배포기에 뜬 뒤): logins 와 HostingApps.allowed 의 logins 분기를 지운다
    val logins: List<String>? = null,
) : HostingFile

data class HostingRepoItem(val id: Long? = null, val fullName: String, val private: Boolean = false)

/** repo 는 업로드 명령에 없다. 명령의 결과는 commandId 로 찾는다(push 가 같은 파일을 덮어쓴다). */
data class SkippedPush(
    override val version: Int,
    val sender: HostingAccount,
    val repo: HostingRepo? = null,
    val commandId: String? = null,
    val app: String? = null,
    val reason: String,
    val at: Instant,
) : HostingFile
