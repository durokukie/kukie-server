package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class EksAccessDeniedException : BusinessException(ClusterErrorCode.EKS_ACCESS_DENIED)
