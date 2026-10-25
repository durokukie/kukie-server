package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class NamespaceNotFoundException(
    // Swagger 의 ApiErrorResponses 가 인자 없이 만들어 오류 코드를 읽는다
    namespace: String? = null,
) : BusinessException(
    ClusterErrorCode.NAMESPACE_NOT_FOUND,
    namespace?.let {
        "클러스터에서 $it 네임스페이스를 찾지 못했습니다(없거나 아직 준비 중입니다). 없다면 kubectl create namespace $it 로 만들어 주세요."
    }
        ?: ClusterErrorCode.NAMESPACE_NOT_FOUND.message,
)
