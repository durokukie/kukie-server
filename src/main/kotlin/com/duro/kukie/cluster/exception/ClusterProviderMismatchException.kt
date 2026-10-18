package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ClusterProviderMismatchException : BusinessException(ClusterErrorCode.CLUSTER_PROVIDER_MISMATCH)
