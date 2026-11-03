package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class NodeGroupBusyException : BusinessException(ClusterErrorCode.NODE_GROUP_BUSY)
