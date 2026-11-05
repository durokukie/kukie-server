package com.duro.kukie.serving.exception

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ServingErrorCode(override val status: HttpStatus, override val message: String) : ErrorCode {
    SERVING_ACCESS_NOT_FOUND(HttpStatus.NOT_FOUND, "API 이용 권한을 찾을 수 없습니다."),
    SERVING_ACCESS_NOT_ACTIVE(HttpStatus.CONFLICT, "처리할 수 없는 API 이용 권한입니다."),
    SERVING_INVITATION_EXPIRED(HttpStatus.GONE, "API 이용 초대가 만료되었습니다."),
    SERVING_ACCESS_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 초대했거나 API 이용이 허용된 사용자입니다."),
    ;
    override val code: String get() = name
}

class ServingAccessException(code: ServingErrorCode) : BusinessException(code)
