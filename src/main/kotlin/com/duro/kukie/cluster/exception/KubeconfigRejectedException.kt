package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class KubeconfigRejectedException(
    reason: String = ClusterErrorCode.KUBECONFIG_REJECTED.message,
) : BusinessException(ClusterErrorCode.KUBECONFIG_REJECTED, reason)
