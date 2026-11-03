package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class GpuNodeControlDeniedException : BusinessException(ClusterErrorCode.GPU_NODE_CONTROL_DENIED)
