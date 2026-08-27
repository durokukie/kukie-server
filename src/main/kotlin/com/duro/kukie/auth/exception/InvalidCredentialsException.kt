package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidCredentialsException : BusinessException(AuthErrorCode.INVALID_CREDENTIALS)
