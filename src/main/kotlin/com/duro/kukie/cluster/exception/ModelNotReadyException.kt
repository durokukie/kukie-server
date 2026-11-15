package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ModelNotReadyException : BusinessException(ClusterErrorCode.MODEL_NOT_READY)
