package com.duro.kukie.cluster.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonValue

enum class ClusterSetupStatus(@get:JsonValue val value: String) {
    OK("ok"),

    /** kubeconfig 로 등록한 클러스터처럼 AWS 역할로 연결하지 않아 구성을 읽을 수 없다. */
    UNSUPPORTED("unsupported"),

    /** 고객 역할이 템플릿 v3 이전이라 애드온·노드 그룹·서브넷을 읽지 못했다. */
    PERMISSION_REQUIRED("permission_required"),
}
