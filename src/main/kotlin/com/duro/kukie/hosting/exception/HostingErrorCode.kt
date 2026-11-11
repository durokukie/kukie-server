package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class HostingErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    HOSTING_APP_NOT_FOUND(HttpStatus.NOT_FOUND, "호스팅 앱을 찾을 수 없습니다."),
    HOSTING_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "빌드 로그가 없습니다. 빌드가 끝나기 전이거나 보관 기간(30일)이 지났습니다."),
    HOSTING_COMMAND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "방금 같은 요청을 보냈습니다. 잠시 뒤 다시 시도해 주세요."),
    HOSTING_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "호스팅 정보를 가져오지 못했습니다. 잠시 뒤 다시 시도해 주세요."),
    HOSTING_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "이 서버에서는 호스팅을 쓸 수 없습니다."),
    ;

    override val code: String
        get() = name
}
