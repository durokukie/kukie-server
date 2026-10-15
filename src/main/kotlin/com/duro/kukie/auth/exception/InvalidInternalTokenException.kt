package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidInternalTokenException : BusinessException(AuthErrorCode.INVALID_INTERNAL_TOKEN)
