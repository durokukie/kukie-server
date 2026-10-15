package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ClusterErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 클러스터입니다."),
    KUBECONFIG_REJECTED(HttpStatus.BAD_REQUEST, "받아들일 수 없는 kubeconfig 입니다."),
    NOTHING_TO_UPDATE(HttpStatus.BAD_REQUEST, "바꿀 내용이 없습니다."),
    CLUSTER_NAME_TAKEN(HttpStatus.CONFLICT, "같은 이름의 클러스터가 이미 있습니다."),
    CLUSTER_IN_USE(HttpStatus.CONFLICT, "이 클러스터를 쓰는 대화가 있어 삭제할 수 없습니다."),
    CLUSTER_CHANGED(HttpStatus.CONFLICT, "이 대화가 쓰던 클러스터의 접속 대상이 바뀌었습니다. 새 대화를 시작해 주세요."),
    ;

    override val code: String
        get() = name
}
