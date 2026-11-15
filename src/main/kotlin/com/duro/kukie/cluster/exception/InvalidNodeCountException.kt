package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidNodeCountException : BusinessException(ClusterErrorCode.INVALID_NODE_COUNT)
