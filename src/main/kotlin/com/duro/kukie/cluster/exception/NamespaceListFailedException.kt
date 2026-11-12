package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class NamespaceListFailedException : BusinessException(ClusterErrorCode.NAMESPACE_LIST_FAILED)
