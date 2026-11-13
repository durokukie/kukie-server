package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ClusterAlreadyRegisteredException : BusinessException(ClusterErrorCode.CLUSTER_ALREADY_REGISTERED)
