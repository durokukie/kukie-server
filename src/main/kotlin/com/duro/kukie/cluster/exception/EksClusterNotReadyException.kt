package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class EksClusterNotReadyException : BusinessException(ClusterErrorCode.EKS_CLUSTER_NOT_READY)
