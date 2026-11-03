package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class GpuNodeGroupNotFoundException : BusinessException(ClusterErrorCode.GPU_NODE_GROUP_NOT_FOUND)
