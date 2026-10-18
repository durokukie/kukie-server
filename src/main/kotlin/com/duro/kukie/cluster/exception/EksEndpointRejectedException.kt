package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class EksEndpointRejectedException(
    reason: String = ClusterErrorCode.EKS_ENDPOINT_REJECTED.message,
) : BusinessException(ClusterErrorCode.EKS_ENDPOINT_REJECTED, reason)
