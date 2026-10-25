package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class NamespaceNotFoundException(
    // Swagger 의 ApiErrorResponses 가 인자 없이 만들어 오류 코드를 읽는다
    namespace: String? = null,
) : BusinessException(
    ClusterErrorCode.NAMESPACE_NOT_FOUND,
    namespace?.let { "클러스터에 $it 네임스페이스가 없습니다. kubectl create namespace $it 로 만들어 주세요." }
        ?: ClusterErrorCode.NAMESPACE_NOT_FOUND.message,
)
