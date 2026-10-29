package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class GitOpsErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    INVALID_REPO_URL(
        HttpStatus.BAD_REQUEST,
        "GitHub 레포 링크가 아닙니다. https://github.com/owner/repo 나 git@github.com:owner/repo.git 모양으로 넣어 주세요.",
    ),
    INVALID_INSTALL_STATE(
        HttpStatus.BAD_REQUEST,
        "GitHub App 설치 요청이 맞지 않거나 만료되었습니다. Kukie 에서 설치를 다시 시작해 주세요.",
    ),
    INSTALLATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Kukie GitHub App 설치를 찾을 수 없습니다."),
    INSTALLATION_NOT_VERIFIED(
        HttpStatus.FORBIDDEN,
        "이 GitHub App 설치를 이 팀에 묶을 수 없습니다. Kukie 에서 설치를 다시 시작해 주세요.",
    ),
    INSTALLATION_TAKEN(HttpStatus.CONFLICT, "이 GitHub App 설치는 다른 팀에 연결되어 있습니다."),
    GITHUB_APP_NOT_INSTALLED(
        HttpStatus.CONFLICT,
        "이 레포에 Kukie GitHub App 권한이 없습니다. GitHub 에서 권한을 준 뒤 다시 시도해 주세요.",
    ),
    GITHUB_APP_SUSPENDED(
        HttpStatus.CONFLICT,
        "GitHub App 설치가 일시 중지되었습니다. GitHub 설정에서 다시 켜 주세요.",
    ),
    MANIFEST_REPO_NOT_CONNECTED(HttpStatus.NOT_FOUND, "이 클러스터에 연결된 레포가 아닙니다."),
    GITHUB_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "GitHub 에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요."),
    ;

    override val code: String
        get() = name
}
