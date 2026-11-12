package com.duro.kukie.chat.exception

import com.duro.kukie.global.exception.BusinessException

class ChatNamespaceNotAllowedException : BusinessException(ChatErrorCode.NAMESPACE_NOT_ALLOWED)
