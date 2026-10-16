package com.duro.kukie.chat.exception

import com.duro.kukie.global.exception.BusinessException

class ActiveRunExistsException : BusinessException(ChatErrorCode.ACTIVE_RUN_EXISTS)
