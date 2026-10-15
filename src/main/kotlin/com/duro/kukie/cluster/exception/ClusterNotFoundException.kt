package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ClusterNotFoundException : BusinessException(ClusterErrorCode.NOT_FOUND)
