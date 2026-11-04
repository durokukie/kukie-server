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
    CLUSTER_HAS_ACTIVE_RUN(HttpStatus.CONFLICT, "이 클러스터의 대화에 아직 끝나지 않은 요청이 있어 삭제할 수 없습니다. 요청이 끝난 뒤 다시 시도해 주세요."),
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
        "KukieAccess 역할에 이 작업 권한이 없습니다. Kukie 템플릿을 고치지 않고 스택을 만들었는지, 스택 업데이트가 끝났는지 확인해 주세요.",
    ),
    EKS_NAMESPACE_NOT_ALLOWED(
        HttpStatus.FORBIDDEN,
        "제한된 권한(limited)으로 연결한 AWS 계정입니다. 스택에서 허용한 namespace(AllowedNamespaces)인지 확인해 주세요.",
    ),
    EKS_ENDPOINT_REJECTED(HttpStatus.BAD_REQUEST, "받아들일 수 없는 클러스터 주소입니다."),
    NAMESPACE_NOT_FOUND(
        HttpStatus.BAD_REQUEST,
        "클러스터에서 그 네임스페이스를 찾지 못했습니다(없거나 아직 준비 중입니다). 없다면 kubectl create namespace 로 만들어 주세요.",
    ),
    NAMESPACE_GRANT_PENDING(
        HttpStatus.CONFLICT,
        "Kukie 권한이 아직 클러스터에 반영되지 않아 네임스페이스를 확인하지 못했습니다. 잠시 뒤 다시 시도해 주세요. " +
            "몇 분이 지나도 같으면 EKS 콘솔의 접근 항목에서 KukieAccess 항목을 지운 뒤 다시 시도해 주세요.",
    ),
    GPU_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "AWS 역할로 연결한 EKS 클러스터에서만 할 수 있는 작업입니다."),
    GPU_NODE_CONTROL_DENIED(
        HttpStatus.FORBIDDEN,
        "GPU 노드 수를 바꿀 권한이 없습니다. CloudFormation 스택의 AccessLevel 이 full, GpuNodeControl 이 true 이고 GpuClusterPattern 이 이 클러스터에 맞는지 확인해 주세요.",
    ),
    GPU_NODE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "이 클러스터에서 그 GPU 노드 그룹을 찾을 수 없습니다."),
    NODE_GROUP_BUSY(HttpStatus.CONFLICT, "노드 그룹이 다른 변경을 처리하고 있습니다. 잠시 뒤 다시 시도해 주세요."),
    INVALID_NODE_COUNT(HttpStatus.BAD_REQUEST, "노드 수는 노드 그룹의 최소값 이상, 최대값 이하여야 합니다."),
    SERVED_MODEL_EXISTS(HttpStatus.CONFLICT, "이미 올라간 모델이 있습니다. 클러스터에 모델 하나만 올릴 수 있으니 먼저 중지해 주세요."),
    SERVED_MODEL_NOT_FOUND(HttpStatus.NOT_FOUND, "그 모델을 찾을 수 없습니다."),
    GPU_ADDON_NOT_INSTALLED(HttpStatus.CONFLICT, "클러스터에 GPU 애드온이 없습니다. 애드온과 llm namespace 를 설치했는지 확인해 주세요."),
    GPU_SERVING_DENIED(
        HttpStatus.FORBIDDEN,
        "모델을 다룰 권한이 없습니다. AWS 연결이 유지되어 있고 CloudFormation 스택의 AccessLevel 이 full 인지 확인해 주세요.",
    ),
    GPU_CLUSTER_API_FAILED(HttpStatus.BAD_GATEWAY, "AWS 나 클러스터가 요청을 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요."),
    MODEL_NOT_READY(HttpStatus.CONFLICT, "모델이 실행 중이 아닙니다. GPU 화면에서 상태를 확인해 주세요."),
    MODEL_CHAT_BUSY(HttpStatus.TOO_MANY_REQUESTS, "진행 중인 대화가 있거나 모델이 요청을 더 받을 수 없습니다. 잠시 뒤 다시 시도해 주세요."),
    MODEL_CHAT_REJECTED(HttpStatus.BAD_REQUEST, "모델이 이 요청을 받지 않았습니다. 대화가 너무 길면 지우고 다시 시도해 주세요."),
    MODEL_CHAT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "대화가 너무 깁니다. 대화를 지우거나 줄여 주세요."),
    MODEL_CHAT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "응답 시간 상한이 지나 끊었습니다. 최대 토큰을 줄여 다시 시도해 주세요."),
    ;

    override val code: String
        get() = name
}
