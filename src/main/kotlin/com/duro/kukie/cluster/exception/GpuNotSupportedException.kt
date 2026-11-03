package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class GpuNotSupportedException : BusinessException(ClusterErrorCode.GPU_NOT_SUPPORTED)
