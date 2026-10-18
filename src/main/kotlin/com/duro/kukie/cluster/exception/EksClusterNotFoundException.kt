package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class EksClusterNotFoundException : BusinessException(ClusterErrorCode.EKS_CLUSTER_NOT_FOUND)
