package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class GpuClusterApiFailedException : BusinessException(ClusterErrorCode.GPU_CLUSTER_API_FAILED)
