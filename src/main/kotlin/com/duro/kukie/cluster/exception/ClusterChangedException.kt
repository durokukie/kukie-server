package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ClusterChangedException : BusinessException(ClusterErrorCode.CLUSTER_CHANGED)
