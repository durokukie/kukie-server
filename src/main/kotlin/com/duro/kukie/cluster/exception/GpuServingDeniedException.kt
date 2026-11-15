package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class GpuServingDeniedException : BusinessException(ClusterErrorCode.GPU_SERVING_DENIED)
