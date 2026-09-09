package com.duro.kukie.notification.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class NotificationErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."),
    NOT_MY_NOTIFICATION(HttpStatus.FORBIDDEN, "자신의 알림만 처리할 수 있습니다."),
    ;

    override val code: String
        get() = name
}
