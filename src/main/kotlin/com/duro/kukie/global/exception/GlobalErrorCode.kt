package com.duro.kukie.global.exception

import org.springframework.http.HttpStatus

enum class GlobalErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");

    override val code: String
        get() = name
}