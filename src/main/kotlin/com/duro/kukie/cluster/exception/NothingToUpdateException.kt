package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class NothingToUpdateException : BusinessException(ClusterErrorCode.NOTHING_TO_UPDATE)
