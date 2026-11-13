package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class HostingErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    HOSTING_APP_NOT_FOUND(HttpStatus.NOT_FOUND, "호스팅 앱을 찾을 수 없습니다."),
    HOSTING_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "빌드 로그가 없습니다. 빌드가 끝나기 전이거나 보관 기간(30일)이 지났습니다."),
    HOSTING_REPOSITORY_NOT_FOUND(HttpStatus.NOT_FOUND, "배포할 수 있는 레포가 아닙니다. GitHub App 에 이 레포를 허용했는지 확인해 주세요."),
    HOSTING_NOT_ALLOWED(HttpStatus.FORBIDDEN, "이 GitHub 계정은 아직 호스팅을 쓸 수 있는 계정이 아닙니다."),
    HOSTING_APP_SOURCE_MISMATCH(HttpStatus.CONFLICT, "앱을 만든 방법과 맞지 않는 요청입니다. 업로드로 만든 앱은 다시 올리거나 레포를 연결하고, 레포에 연결된 앱은 그 레포에 push 해 주세요."),
    HOSTING_REPOSITORY_IN_USE(HttpStatus.CONFLICT, "이 레포는 이미 다른 앱을 배포하고 있습니다. 레포 하나에 앱 하나만 연결할 수 있습니다."),
    HOSTING_AGENT_DENIED(HttpStatus.FORBIDDEN, "사용자가 에이전트의 로그인을 거부했습니다."),
    HOSTING_AGENT_EXPIRED(HttpStatus.GONE, "로그인 요청이 만료됐거나 이미 처리됐습니다. 처음부터 다시 로그인해 주세요."),
    HOSTING_GITHUB_NOT_LINKED(HttpStatus.CONFLICT, "GitHub 계정을 먼저 연결해 주세요. 호스팅 앱의 주인은 GitHub 계정입니다."),
    HOSTING_TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요."),
    HOSTING_INVALID_APP_NAME(HttpStatus.BAD_REQUEST, "앱 이름은 영소문자로 시작하고 영소문자·숫자·하이픈(연달아 쓰지 않음)으로 30자 이하여야 합니다."),
    HOSTING_UPLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "업로드는 50MB 까지입니다. 의존성·빌드 결과물·데이터 파일을 빼고 다시 묶어 주세요."),
    HOSTING_APP_LIMIT(HttpStatus.CONFLICT, "앱은 3개까지 만들 수 있습니다. 앱을 지우거나 이미 있는 앱 이름으로 배포해 주세요."),
    HOSTING_DEPLOY_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 배포가 있습니다. 그 배포가 끝난 뒤 다시 시도해 주세요."),
    HOSTING_UPLOAD_MISSING(HttpStatus.CONFLICT, "업로드가 없거나 크기가 배포를 만들 때와 다릅니다. 파일을 다시 올려 주세요."),
    HOSTING_DEPLOY_NOT_STARTED(HttpStatus.CONFLICT, "아직 시작하지 않은 배포입니다. 먼저 시작해 주세요."),
    HOSTING_DEPLOY_NOT_FOUND(HttpStatus.NOT_FOUND, "배포를 찾을 수 없습니다. 7일이 지났거나 다른 계정의 배포입니다."),
    HOSTING_TOO_MANY_DEPLOYS(HttpStatus.TOO_MANY_REQUESTS, "한 시간에 배포를 너무 많이 만들었습니다. 잠시 뒤 다시 시도해 주세요."),
    HOSTING_COMMAND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "방금 같은 요청을 보냈습니다. 잠시 뒤 다시 시도해 주세요."),
    HOSTING_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "호스팅 정보를 가져오지 못했습니다. 잠시 뒤 다시 시도해 주세요."),
    HOSTING_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "이 서버에서는 호스팅을 쓸 수 없습니다."),
    ;

    override val code: String
        get() = name
}
