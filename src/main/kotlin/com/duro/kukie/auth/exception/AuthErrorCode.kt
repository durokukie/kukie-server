package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    OAUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),
    CROSS_SITE_COOKIE(HttpStatus.FORBIDDEN, "다른 오리진에서 시작됐거나 출처(Sec-Fetch-Site)를 알 수 없는 요청은 쿠키로 인증하지 않습니다. 브라우저는 HTTPS·localhost 에서만 이 헤더를 보냅니다."),
    ;

    override val code: String
        get() = name
}
