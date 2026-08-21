package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidTokenException : BusinessException(AuthErrorCode.INVALID_TOKEN)
