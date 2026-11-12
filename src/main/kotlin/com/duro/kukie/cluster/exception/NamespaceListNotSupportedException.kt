package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class NamespaceListNotSupportedException : BusinessException(ClusterErrorCode.NAMESPACE_LIST_NOT_SUPPORTED)
