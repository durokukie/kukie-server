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
    CLUSTER_PROVIDER_MISMATCH(HttpStatus.BAD_REQUEST, "EKS 클러스터는 kubeconfig 나 namespace 를 바꿀 수 없습니다. 다시 등록해 주세요."),
    INVALID_NAMESPACE(
        HttpStatus.BAD_REQUEST,
        "namespace 는 소문자·숫자·하이픈으로 된 63자 이하 이름이어야 하며 kube- 로 시작할 수 없습니다.",
    ),
    AWS_CONNECTION_NOT_STARTED(HttpStatus.NOT_FOUND, "AWS 연결을 먼저 시작해 주세요."),
    AWS_ACCOUNT_MISMATCH(HttpStatus.CONFLICT, "이 AWS 연결은 다른 계정에 연결되어 있습니다."),
    AWS_REGION_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 AWS 리전입니다."),
    AWS_ROLE_NOT_ASSUMABLE(
        HttpStatus.BAD_REQUEST,
        "AWS 계정의 KukieAccess 역할을 맡을 수 없습니다. 계정 ID 와 CloudFormation 스택이 만들어졌는지 확인해 주세요.",
    ),
    EKS_CLUSTER_NOT_FOUND(HttpStatus.NOT_FOUND, "AWS 계정에서 그 EKS 클러스터를 찾을 수 없습니다."),
    EKS_CLUSTER_NOT_READY(HttpStatus.CONFLICT, "EKS 클러스터가 아직 준비되지 않았습니다. 잠시 뒤 다시 시도해 주세요."),
    EKS_ACCESS_ENTRY_DISABLED(
        HttpStatus.BAD_REQUEST,
        "EKS 클러스터의 인증 모드가 API 또는 API_AND_CONFIG_MAP 이어야 합니다.",
    ),
    EKS_ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        "KukieAccess 역할에 이 작업 권한이 없습니다. 스택에서 허용한 namespace(AllowedNamespaces)인지 확인해 주세요.",
    ),
    EKS_ENDPOINT_REJECTED(HttpStatus.BAD_REQUEST, "받아들일 수 없는 클러스터 주소입니다."),
    NAMESPACE_NOT_FOUND(
        HttpStatus.BAD_REQUEST,
        "클러스터에서 그 네임스페이스를 찾지 못했습니다(없거나 아직 준비 중입니다). 없다면 kubectl create namespace 로 만들어 주세요.",
    ),
    ;

    override val code: String
        get() = name
}
