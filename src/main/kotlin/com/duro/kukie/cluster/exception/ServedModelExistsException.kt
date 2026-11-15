package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ServedModelExistsException : BusinessException(ClusterErrorCode.SERVED_MODEL_EXISTS)
