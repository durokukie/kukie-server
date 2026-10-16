package com.duro.kukie.chat.exception

import com.duro.kukie.global.exception.BusinessException

class RequestMismatchException : BusinessException(ChatErrorCode.REQUEST_MISMATCH)
