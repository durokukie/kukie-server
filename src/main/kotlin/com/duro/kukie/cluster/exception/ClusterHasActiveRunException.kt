package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ClusterHasActiveRunException : BusinessException(ClusterErrorCode.CLUSTER_HAS_ACTIVE_RUN)
