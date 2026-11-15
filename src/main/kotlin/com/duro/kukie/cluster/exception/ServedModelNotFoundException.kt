package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ServedModelNotFoundException : BusinessException(ClusterErrorCode.SERVED_MODEL_NOT_FOUND)
