package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ClusterInUseException : BusinessException(ClusterErrorCode.CLUSTER_IN_USE)
