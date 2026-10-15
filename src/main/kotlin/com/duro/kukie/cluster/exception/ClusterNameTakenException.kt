package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ClusterNameTakenException : BusinessException(ClusterErrorCode.CLUSTER_NAME_TAKEN)
