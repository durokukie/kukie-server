package com.duro.kukie.user.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidCurrentPasswordException : BusinessException(UserErrorCode.INVALID_CURRENT_PASSWORD)
