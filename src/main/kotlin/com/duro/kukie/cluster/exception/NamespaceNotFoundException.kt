package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class NamespaceNotFoundException(
    namespace: String? = null,
) : BusinessException(
    ClusterErrorCode.NAMESPACE_NOT_FOUND,
    namespace?.let { "클러스터에 $it 네임스페이스가 없습니다. kubectl create namespace $it 로 만들어 주세요." }
        ?: ClusterErrorCode.NAMESPACE_NOT_FOUND.message,
)
