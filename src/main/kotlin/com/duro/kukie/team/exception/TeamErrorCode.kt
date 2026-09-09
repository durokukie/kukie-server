package com.duro.kukie.team.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class TeamErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."),
    NOT_TEAM_MEMBER(HttpStatus.FORBIDDEN, "해당 팀의 구성원이 아닙니다."),
    NOT_TEAM_ADMIN(HttpStatus.FORBIDDEN, "팀 관리자만 할 수 있습니다."),
    ADMIN_REQUIRED(HttpStatus.CONFLICT, "팀에는 관리자가 최소 1명 있어야 합니다."),
    ;

    override val code: String
        get() = name
}
