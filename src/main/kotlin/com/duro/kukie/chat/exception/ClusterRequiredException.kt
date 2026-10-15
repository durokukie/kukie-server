package com.duro.kukie.chat.exception

import com.duro.kukie.global.exception.BusinessException

class ClusterRequiredException : BusinessException(ChatErrorCode.CLUSTER_REQUIRED)
