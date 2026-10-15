package com.duro.kukie.chat.exception

import com.duro.kukie.global.exception.BusinessException

class RunNotFoundException : BusinessException(ChatErrorCode.RUN_NOT_FOUND)
