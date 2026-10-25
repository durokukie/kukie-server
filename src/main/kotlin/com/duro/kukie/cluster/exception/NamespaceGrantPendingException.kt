package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class NamespaceGrantPendingException : BusinessException(ClusterErrorCode.NAMESPACE_GRANT_PENDING)
