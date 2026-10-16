package com.duro.kukie.chat.exception

import com.duro.kukie.global.exception.BusinessException

class PrivateSessionException : BusinessException(ChatErrorCode.PRIVATE_SESSION)
