package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class EksNamespaceNotAllowedException : BusinessException(ClusterErrorCode.EKS_NAMESPACE_NOT_ALLOWED)
