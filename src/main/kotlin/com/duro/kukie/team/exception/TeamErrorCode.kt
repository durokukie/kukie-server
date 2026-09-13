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
    CANNOT_REMOVE_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 추방할 수 없습니다. 팀 나가기를 이용하세요."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 초대입니다."),
    NOT_MY_INVITATION(HttpStatus.FORBIDDEN, "자신에게 온 초대만 처리할 수 있습니다."),
    INVITATION_NOT_PENDING(HttpStatus.CONFLICT, "이미 처리된 초대입니다."),
    INVITATION_EXPIRED(HttpStatus.GONE, "만료된 초대입니다. 다시 초대를 받아야 합니다."),
    INVITATION_ALREADY_SENT(HttpStatus.CONFLICT, "이미 초대한 주소입니다."),
    ALREADY_TEAM_MEMBER(HttpStatus.CONFLICT, "이미 해당 팀의 구성원입니다."),
    ;

    override val code: String
        get() = name
}
