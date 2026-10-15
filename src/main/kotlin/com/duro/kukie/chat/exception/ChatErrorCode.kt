package com.duro.kukie.chat.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ChatErrorCode(
    override val status: HttpStatus,
    override val message: String,
    private val responseCode: String? = null,
) : ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 대화입니다."),
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 요청 기록입니다.", "NOT_FOUND"),
    PRIVATE_SESSION(HttpStatus.FORBIDDEN, "Private 대화에서는 클러스터를 변경할 수 없습니다. 변경은 Shared 대화에서 해 주세요."),
    CLUSTER_REQUIRED(HttpStatus.BAD_REQUEST, "클러스터를 골라야 대화를 만들 수 있습니다. 팀 설정에서 클러스터를 먼저 연결해 주세요."),
    CLUSTER_TEAM_MISMATCH(HttpStatus.BAD_REQUEST, "고른 클러스터의 팀과 teamId 가 다릅니다. 대화의 팀은 클러스터가 정합니다."),
    REQUEST_MISMATCH(HttpStatus.CONFLICT, "같은 requestId 로 다른 요청을 보냈습니다."),
    ACTIVE_RUN_EXISTS(HttpStatus.CONFLICT, "이 대화에 아직 끝나지 않은 요청이 있습니다."),
    ;

    override val code: String
        get() = responseCode ?: name
}
