package com.duro.kukie.actionplan.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ActionPlanErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 Action Plan 입니다."),
    PLAN_STATE_CONFLICT(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 동작입니다."),
    PLAN_CALL_MISMATCH(HttpStatus.CONFLICT, "승인하려는 호출이 계획과 다릅니다."),
    ;

    override val code: String
        get() = name
}
