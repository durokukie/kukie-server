package com.duro.kukie.serving.exception

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ServingErrorCode(override val status: HttpStatus, override val message: String) : ErrorCode {
    SERVING_ACCESS_NOT_FOUND(HttpStatus.NOT_FOUND, "API 이용 권한을 찾을 수 없습니다."),
    SERVING_ACCESS_NOT_ACTIVE(HttpStatus.CONFLICT, "처리할 수 없는 API 이용 권한입니다."),
    SERVING_INVITATION_EXPIRED(HttpStatus.GONE, "API 이용 초대가 만료되었습니다."),
    SERVING_ACCESS_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 초대했거나 API 이용이 허용된 사용자입니다."),
    SERVING_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "API 키를 찾을 수 없습니다."),
    SERVING_KEY_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 API 키입니다."),
    SERVING_KEY_LIMIT_REACHED(HttpStatus.CONFLICT, "유효한 API 키는 대상별 최대 5개입니다."),
    SERVING_DESTINATION_REJECTED(HttpStatus.BAD_REQUEST, "허용된 공개 HTTPS 카운터 주소가 아닙니다."),
    SERVING_ENDPOINT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "API 제공 대상이 연결되지 않았습니다."),
    SERVING_RELAY_BUSY(HttpStatus.TOO_MANY_REQUESTS, "동시 API 요청 한도를 초과했습니다."),
    SERVING_MODEL_DENIED(HttpStatus.FORBIDDEN, "허용되지 않은 모델입니다."),
    SERVING_UPSTREAM_FAILED(HttpStatus.BAD_GATEWAY, "고객 카운터에 연결할 수 없습니다."),
    SERVING_UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "고객 카운터 응답 시간이 초과되었습니다."),
    ;
    override val code: String get() = name
}

class ServingAccessException(code: ServingErrorCode) : BusinessException(code)
