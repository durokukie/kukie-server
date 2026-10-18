package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidNamespaceException : BusinessException(ClusterErrorCode.INVALID_NAMESPACE)
