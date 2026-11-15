package com.duro.kukie.cluster.exception

import com.duro.kukie.global.exception.BusinessException

class ModelChatTimeoutException : BusinessException(ClusterErrorCode.MODEL_CHAT_TIMEOUT)
