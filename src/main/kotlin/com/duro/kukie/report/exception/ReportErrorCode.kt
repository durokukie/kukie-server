package com.duro.kukie.report.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ReportErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 보고서입니다."),
    REPORT_NOT_READY(HttpStatus.CONFLICT, "아직 만들고 있는 보고서입니다."),
    REPORT_CHANGED(HttpStatus.CONFLICT, "다른 사람이 먼저 고친 보고서입니다. 새로 불러온 뒤 다시 고쳐 주세요."),
    REPORT_TOO_MANY(HttpStatus.TOO_MANY_REQUESTS, "보고서는 한 시간에 30개까지 만들 수 있습니다. 잠시 뒤 다시 시도해 주세요."),
    ;

    override val code: String
        get() = name
}
